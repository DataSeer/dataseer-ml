package org.grobid.core.jni;

import org.grobid.core.GrobidModel;
import org.grobid.core.GrobidModels;
import org.grobid.core.exceptions.GrobidException;
import org.grobid.core.utilities.GrobidProperties;
import org.grobid.core.utilities.IOUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;  
import java.io.*;
import java.lang.StringBuilder;
import java.util.*;
import java.util.regex.*;

import jep.Jep;
import jep.JepConfig;
import jep.JepException;

import java.util.function.Consumer;

/**
 * 
 * @author: Patrice
 */
public class DeLFTClassifierModel {
    public static final Logger LOGGER = LoggerFactory.getLogger(DeLFTClassifierModel.class);

    // Exploit JNI CPython interpreter to execute load and execute a DeLFT deep learning model 
    private String modelName;
    private String architecture;

    public DeLFTClassifierModel(String model, String architecture) {
        this.modelName = model.replace("-", "_");
        this.architecture = architecture;
        try {
            LOGGER.info("Loading DeLFT classification model for " + model + "in " + GrobidProperties.getInstance().getModelPath() + "...");
            JEPThreadPoolClassifier.getInstance().run(new InitModel(this.modelName, GrobidProperties.getInstance().getModelPath(), this.architecture));
        } catch(InterruptedException e) {
            LOGGER.error("DeLFT model " + this.modelName + " initialization failed", e);
        }
    }

    class InitModel implements Runnable { 
        private String modelName;
        private String architecture;
        private File modelPath;
          
        public InitModel(String modelName, File modelPath, String architecture) { 
            this.modelName = modelName;
            this.modelPath = modelPath;
            this.architecture = architecture;
        } 
          
        @Override
        public void run() { 
            Jep jep = JEPThreadPoolClassifier.getInstance().getJEPInstance(); 
            try { 
                System.out.println("init classifier...");
                jep.eval(this.modelName+" = Classifier('" + this.modelName.replace("_", "-") + "', 'model_type=" + this.architecture + "')");
                jep.eval(this.modelName+".load(dir_path='"+modelPath.getAbsolutePath()+"')");
            } catch(JepException e) {
                throw new GrobidException("DeLFT classifier model initialization failed. ", e);
            }
        } 
    } 

    private class ClassificationTask implements Callable<String> { 
        private List<String> data;
        private String modelName;

        public ClassificationTask(String modelName, List<String> data) { 
            //System.out.println("label thread: " + Thread.currentThread().getId());
            this.modelName = modelName;
            this.data = data;
        }

        private void setJepStringValueWithFileFallback(
            Jep jep, String name, List<String> values
        ) throws JepException, IOException {
            try {
                jep.set(name, values);
            } catch(JepException e) {
                /*File tempFile = IOUtilities.newTempFile(name, ".data");
                LOGGER.debug(
                    "Falling back to file {} due to exception: {}",
                    tempFile, e.toString()
                );
                IOUtilities.writeInFile(tempFile.getAbsolutePath(), values);
                jep.eval("from pathlib import Path");
                jep.eval(
                    name + " = Path('" + tempFile.getAbsolutePath() +
                    "').read_text(encoding='utf-8')"
                );
                tempFile.delete();*/
            }
        }

        @Override
        public String call() { 
            Jep jep = JEPThreadPoolClassifier.getInstance().getJEPInstance(); 
            StringBuilder labelledData = new StringBuilder();
            String results = null;
            try {
                System.out.println(this.data);

                // load and classify, input here is an array of texts to classify
                this.setJepStringValueWithFileFallback(jep, "input", this.data);
                jep.eval("print('the input', input)");
                jep.eval("jsondict = "+this.modelName+".predict(input, 'json', use_main_thread_only=True)");
                jep.eval("print(json.dumps(jsondict, sort_keys=False, indent=4, ensure_ascii=False))");
                Object objectResult = jep.getValue("json.dumps(jsondict, sort_keys=False, indent=4, ensure_ascii=False)");

                results = (String) objectResult;

                System.out.println(results);
                // cleaning
                jep.eval("del jsondict");
                jep.eval("del input");
                //jep.eval("K.clear_session()");
            } catch(JepException e) {
                LOGGER.error("DeLFT model classification via JEP failed", e);
            } catch(IOException e) {
                LOGGER.error("DeLFT model classification failed", e);
            }
            //System.out.println(labelledData.toString());
            return results;
        } 
    } 

    /**
     *  Classify an array of string in batch. The result is a json array giving 
     *  for each text the classification results. 
     */
    public String classify(List<String> data) {
        String result = null;
        try {
            result = JEPThreadPoolClassifier.getInstance().call(new ClassificationTask(this.modelName, data));
        } catch(InterruptedException e) {
            LOGGER.error("DeLFT model " + this.modelName + " classification interrupted", e);
        } catch(ExecutionException e) {
            LOGGER.error("DeLFT model " + this.modelName + " classification failed", e);
        }
        return result;
    }

    /**
     * Training via JNI CPython interpreter (JEP). It appears that after some epochs, the JEP thread
     * usually hangs... Possibly issues with IO threads at the level of JEP (output not consumed because
     * of \r and no end of line?). 
     */
    public static void trainJNI(String modelName, File trainingData, File outputModel) {
        try {
            LOGGER.info("Train DeLFT classification model " + modelName + "...");
            JEPThreadPoolClassifier.getInstance().run(new TrainTask(modelName, trainingData, GrobidProperties.getInstance().getModelPath()));
        } catch(InterruptedException e) {
            LOGGER.error("Train DeLFT classification model " + modelName + " task failed", e);
        }
    }

    private static class TrainTask implements Runnable { 
        private String modelName;
        private File trainPath;
        private File modelPath;

        public TrainTask(String modelName, File trainPath, File modelPath) { 
            //System.out.println("train thread: " + Thread.currentThread().getId());
            this.modelName = modelName;
            this.trainPath = trainPath;
            this.modelPath = modelPath;
        } 
          
        @Override
        public void run() { 
            Jep jep = JEPThreadPoolClassifier.getInstance().getJEPInstance(); 
            try {
                // load data
                jep.eval("x_all, y_all, f_all = load_data_and_labels_crf_file('" + this.trainPath.getAbsolutePath() + "')");
                jep.eval("x_train, x_valid, y_train, y_valid = train_test_split(x_all, y_all, test_size=0.1)");
                jep.eval("print(len(x_train), 'train sequences')");
                jep.eval("print(len(x_valid), 'validation sequences')");

                String useELMo = "False";
                if (GrobidProperties.getInstance().useELMo()) {
                    useELMo = "True";
                }

                // init model to be trained
                jep.eval("model = Classifier('"+this.modelName+
                    "', max_epoch=100, recurrent_dropout=0.50, embeddings_name='glove-840B', use_ELMo="+useELMo+")");

                // actual training
                //start_time = time.time()
                jep.eval("model.train(x_train, y_train, x_valid, y_valid)");
                //runtime = round(time.time() - start_time, 3)
                //print("training runtime: %s seconds " % (runtime))

                // saving the model
                System.out.println(this.modelPath.getAbsolutePath());
                jep.eval("model.save('"+this.modelPath.getAbsolutePath()+"')");
                
                // cleaning
                jep.eval("del x_all");
                jep.eval("del y_all");
                jep.eval("del f_all");
                jep.eval("del x_train");
                jep.eval("del x_valid");
                jep.eval("del y_train");
                jep.eval("del y_valid");
                jep.eval("del model");
            } catch(JepException e) {
                LOGGER.error("DeLFT classification model training via JEP failed", e);
            } 
        } 
    } 

    /**
     *  Train with an external process rather than with JNI, this approach appears to be more stable for the
     *  training process (JNI approach hangs after a while) and does not raise any runtime/integration issues. 
     */
    public static void train(String modelName, File trainingData, File outputModel) {
        try {
            LOGGER.info("Train DeLFT model " + modelName + "...");
            List<String> command = Arrays.asList("python3", 
                "dataseerClassifier.py", 
                modelName,
                "train",
                "--input", trainingData.getAbsolutePath(),
                "--output", GrobidProperties.getInstance().getModelPath().getAbsolutePath());
            if (GrobidProperties.getInstance().useELMo()) {
                command.add("--use-ELMo");
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            File delftPath = new File(GrobidProperties.getInstance().getDeLFTFilePath());
            pb.directory(delftPath);
            Process process = pb.start(); 
            //pb.inheritIO();
            CustomStreamGobbler customStreamGobbler = 
                new CustomStreamGobbler(process.getInputStream(), System.out);
            Executors.newSingleThreadExecutor().submit(customStreamGobbler);
            SimpleStreamGobbler streamGobbler = new SimpleStreamGobbler(process.getErrorStream(), System.err::println);
            Executors.newSingleThreadExecutor().submit(streamGobbler);
            int exitCode = process.waitFor();
            //assert exitCode == 0;
        } catch(IOException e) {
            LOGGER.error("IO error when training DeLFT classification model " + modelName, e);
        } catch(InterruptedException e) {
            LOGGER.error("Train DeLFT classification model " + modelName + " task failed", e);
        }
    }

    public synchronized void close() {
        try {
            LOGGER.info("Close DeLFT classification model " + this.modelName + "...");
            JEPThreadPoolClassifier.getInstance().run(new CloseModel(this.modelName));
        } catch(InterruptedException e) {
            LOGGER.error("Close DeLFT classification model " + this.modelName + " task failed", e);
        }
    }

    private class CloseModel implements Runnable { 
        private String modelName;
          
        public CloseModel(String modelName) { 
            this.modelName = modelName;
        } 
          
        @Override
        public void run() { 
            Jep jep = JEPThreadPoolClassifier.getInstance().getJEPInstance(); 
            try { 
                jep.eval("del "+this.modelName);
            } catch(JepException e) {
                LOGGER.error("Closing DeLFT classification model failed", e);
            } 
        } 
    }

    private static class SimpleStreamGobbler implements Runnable {
        private InputStream inputStream;
        private Consumer<String> consumer;
     
        public SimpleStreamGobbler(InputStream inputStream, Consumer<String> consumer) {
            this.inputStream = inputStream;
            this.consumer = consumer;
        }
     
        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines()
              .forEach(consumer);
        }
    }

    /**
     * This is a custom gobbler that reproduces correctly the Keras training progress bar
     * by injecting a \r for progress line updates. 
     */ 
    private static class CustomStreamGobbler implements Runnable {
        public static final Logger LOGGER = LoggerFactory.getLogger(CustomStreamGobbler.class);

        private final InputStream is;
        private final PrintStream os;
        private Pattern pattern = Pattern.compile("\\d/\\d+ \\[");

        public CustomStreamGobbler(InputStream is, PrintStream os) {
            this.is = is;
            this.os = os;
        }
     
        @Override
        public void run() {
            try {
                InputStreamReader isr = new InputStreamReader(this.is);
                BufferedReader br = new BufferedReader(isr);
                String line = null;
                while ((line = br.readLine()) != null) {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        os.print("\r" + line);
                        os.flush();
                    } else {
                        os.println(line);
                    }
                }
            }
            catch (IOException e) {
                LOGGER.warn("IO error between embedded python and java process", e);
            }
        }
    }

}