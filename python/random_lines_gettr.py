import random
from pathlib import Path

import argparse

def get_random(source: str, target: str, length: int):

    with open(source, 'r') as src, open(target, 'r') as trg:
        sources = src.readlines()
        targets = trg.readlines()
        numbers = random.sample(range(len(sources)), length)
        with open(source, 'w') as src_out, open(target, 'w') as trg_out:
            for num in numbers:
                src_out.write(f'{sources[num]}')
                trg_out.write(f'{targets[num]}')

if __name__ == "__main__":
    """
    Select x% lines from file at random
    """
    parser = argparse.ArgumentParser()
    # Required parameters
    parser.add_argument("--source", type=str, default=None, help="Path to the source file")
    parser.add_argument("--target", type=str, default=None, help="Path to the tahget file")
    parser.add_argument("--length", type=str, default=None, help="Percentage of input file to be randomly copied to output file.")

    args = parser.parse_args()
    # Required parameters
    source = Path(args.source)
    target = Path(args.target)
    length = int(args.length)

    print(f'\tSource file set to {source}')
    print(f'\tTarget file set to {target}')
    print(f'\tNumber of lines to be copied: [{length}]')

    get_random(source, target, length)
    