"""
    Convert the csv data type file into a json structure to be used by DataSeer tools. 
    Install: pip3 install -r requirements.txt
    Usage: python3 converter DataType.csv DataType.json
"""

import json
import argparse
import pandas as pd

def convert_with_pandas(input_path, output_path):
    df = pd.read_csv(input_path)    
    res = {}

    # Data type:Subtype:Sub-subtype;MESH ID;MeSH Tree No.s;Description;Best Practice;Most suitable repositories
    for index, row in df.iterrows():
        rawDataType = row['Data type:Subtype:Sub-subtype'].strip()
        if rawDataType == 'n/a':
            continue
        pieces = rawDataType.split(":")
        dataType = pieces[0]
        dataSubType = None
        dataSubSubType = None
        if len(pieces)>1:
            dataSubType = pieces[1]
            if len(pieces)>2:
                dataSubSubType = pieces[2]

        description = row['Description']
        bestPractice = row['Best Practice']
        mostSuitableRepositories = row['Most suitable repositories']

        meshId = row['MESH ID']
        meshTree= row['MeSH Tree No.s']

        if description == 'XX' or description == 'n/a':
            description = None
        if bestPractice == 'XX' or bestPractice == 'n/a':
            bestPractice = None
        if mostSuitableRepositories == 'XX' or mostSuitableRepositories == 'n/a':
            mostSuitableRepositories = None
        if meshId == 'XX' or meshId == 'n/a':
            meshId = None
        if meshTree == 'XX' or meshTree == 'n/a':
            meshTree = None    

        struct = build_struct(description, bestPractice, mostSuitableRepositories, meshId, meshTree)
        if dataSubType is not None:
            if dataSubSubType is not None:
                if dataType in res:
                    if dataSubType in res[dataType]:
                        res[dataType][dataSubType][dataSubSubType] = struct
                    else:
                        struct2 = {}
                        struct2[dataSubSubType] = struct
                        res[dataType][dataSubType] = struct2
                else: 
                    struct3 = {}
                    struct3[dataSubSubType] = struct
                    struct2 = {}
                    struct2[dataSubType] = struct3
                    res[dataType] = struct2
            else:
                if dataType in res:
                    if dataSubType in res[dataType]:
                        struct2 = res[dataType][dataSubType]
                        for key in struct:
                            struct2[key] = struct[key]
                        res[dataType][dataSubType] = struct2
                    else:
                        res[dataType][dataSubType] = struct
                else:
                    struct2 = {}
                    struct2[dataSubType] = struct
                    res[dataType] = struct2
        else:
            if dataType in res:
                struct2 = res[dataType]
                # merging struct2 and struct
                for key in struct:
                    struct2[key] = struct[key]
                res[dataType] = struct2
            else:
                res[dataType] = struct

    with open(output_path,'w') as out:
        out.write(json.dumps(res, indent=4, sort_keys=True))


def build_struct(description=None, bestPractice=None, mostSuitableRepositories=None, meshId=None, meshTree=None):
    struct = {}
    if description is not None:
        struct['description'] = description
    if bestPractice is not None:
        struct['best_practice'] = bestPractice    
    if mostSuitableRepositories is not None:
        struct['most_suitable_repositories'] = mostSuitableRepositories   
    if meshId is not None:
        struct['mesh_id'] = meshId   
    if meshTree is not None:
        struct['mesh_tree'] = meshTree   
    return struct

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Converter for data type csv files into json")

    parser.add_argument("input")
    parser.add_argument("output")

    args = parser.parse_args()

    input_file = args.input    
    output_file = args.output

    if input_file is None or output_file is None:
        print("Invalid parameters, usage: python3 converter input.csv output.json")
    else: 
        convert_with_pandas(input_file, output_file)
