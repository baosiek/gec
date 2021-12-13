from typing import Dict
import yaml
import os
from posixpath import join
import sys


def fill_vocab(file_path: str, vocab: Dict) -> Dict:
    with open(file_path, "r") as v:
        lines = v.readlines()
        counter = len(vocab)
        for line in lines:
            line_parts = line.split('\t')
            if vocab.get(line_parts[0]) is not None: # sentence piece already in vocab
                continue
            else:
                vocab[line_parts[0]] = counter
                counter += 1

    return vocab

if __name__ == "__main__":
    model = sys.argv[1]
    model_dir = join(os.getcwd(),model)
    model_files = [f for f in os.listdir(model_dir) if join(model_dir, f).endswith('txt')]
    vocab = dict()    

    for file in model_files:
        vocab = fill_vocab(join(model_dir, file), vocab)

    with open(join(os.getcwd(),f"{model}/vocab.yml"), 'w') as yml_file:
        yaml.dump(vocab, yml_file, default_flow_style=False, sort_keys=False)