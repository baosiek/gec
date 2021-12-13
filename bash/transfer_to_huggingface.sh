#!/bin/bash

MODEL=$(pwd)/$1
HUGGINGFACE_DIR="/home/baosiek/Development/dlpt/gec/en_src-en_ref"
L1=src # stands for not english
L2=trg # stands for english
if [ ! -d ${MODEL} ]; then
	echo "Directory ${MODEL} not found."
	exit 1
fi
echo "Transfering from directory ${MODEL}"

# copy generated spms and vocab.yml to be converted to Huggingface
echo "Rename and copy source and target spms to Huggingface"
cp $MODEL/${L1}.spm $HUGGINGFACE_DIR/source.spm
cp $MODEL/${L2}.spm $HUGGINGFACE_DIR/target.spm
cp $MODEL/vocab.yml $HUGGINGFACE_DIR/vocab.yml

cp $MODEL/model.npz.best-bleu-detok.npz.decoder.yml $HUGGINGFACE_DIR/decoder.yml
cp $MODEL/model.npz.best-bleu-detok.npz $HUGGINGFACE_DIR/model.npz

echo "The End!"
