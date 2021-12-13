#!/bin/bash

GPUS=0
MARIAN=../..
TOOLS=$MARIAN/tools
MOSESSCRIPT_TOKENIZER=$TOOLS/moses-scripts/scripts/tokenizer
MOSESSCRIPT_SCRIPTS=$TOOLS/moses-scripts/scripts
TEMP=temp
SRC=src
TRG=trg
MODEL=model
TEST=TEST

if [ -d "$TEMP" ]
then
	rm -r $TEMP
fi

mkdir -p $TEMP

if [ -d "$TEST" ]
then
	rm -r $TEST
fi

mkdir -p $TEST

for lang in $SRC $TRG
do

	cat data/clang8/clang8.$lang.test.txt | ${MOSESSCRIPT_TOKENIZER}/replace-unicode-punctuation.perl > $TEMP/$entry.unicode.$lang
	cat $TEMP/$entry.unicode.$lang | ${MOSESSCRIPT_TOKENIZER}/remove-non-printing-char.perl > $TEMP/$entry.printingchar.$lang
	cat $TEMP/$entry.printingchar.$lang | ${MOSESSCRIPT_TOKENIZER}/normalize-punctuation.perl -l en > $TEMP/$entry.punctuation.$lang
	sed 's/  */ /g;s/^ *//g;s/ *$$//g' $TEMP/$entry.punctuation.$lang > $TEMP/$entry.punctuation.$lang.out
	mv $TEMP/$entry.punctuation.$lang.out $TEMP/$entry.punctuation.$lang
	cat $TEMP/$entry.punctuation.$lang | ${MOSESSCRIPT_TOKENIZER}/tokenizer.perl -q -l en > $TEMP/$entry.tok.$lang
	cat $TEMP/$entry.tok.$lang | $MARIAN/build/spm_encode --model $MODEL/$lang.spm > $TEST/corpus.test.encoded.$lang

done

rm -r $TEMP

java FileCleaner.java "$TEST/corpus.test.encoded.$SRC" "$TEST/corpus.test.encoded.$TRG"

cat $TEST/corpus.test.encoded.$SRC \
    | $MARIAN/build/marian-decoder -c  model/model.npz.orig.npz.decoder.yml -d $GPUS \
      -b 12 -n 0.6 --mini-batch 8 -w 2500 --max-length 200 \
    | sed 's/\@\@ //g' \
    | $MOSESSCRIPT_SCRIPTS/recaser/detruecase.perl 2> /dev/null \
    | $MOSESSCRIPT_SCRIPTS/tokenizer/detokenizer.perl -l en 2>/dev/null \
    | $MOSESSCRIPT_SCRIPTS/generic/multi-bleu-detok.perl $TEST/corpus.test.encoded.$TRG \
    #| sed -r 's/BLEU = ([0-9.]+),.*/\1/'

rm -r $TEST
