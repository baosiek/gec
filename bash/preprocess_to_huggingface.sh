#!/bin/bash

MARIAN=../..
TOOLS=$MARIAN/tools
MOSESSCRIPT_TOKENIZER=$TOOLS/moses-scripts/scripts/tokenizer
CORPUS=corpus
USE_OWT="yes"
DATA=/home/baosiek/Development/marian/examples/transformer/data
HUGGINGFACE_DIR=/home/baosiek/Development/dlpt/gec/en_src-en_ref
L1=n_eng # stands for not english
L2=eng # stands for english
SPM_TYPE=unigram
TEMP=temp

Usage()
{
   # Display Help
   echo
   echo "USAGE"
   echo "Syntax: run-to-huggingface [-g|s|m|f|t|v]"
   echo "options:"
   echo "g     Assign GPU number."
   echo "s     Sets spm vocab size."
   echo "m     Directory where the model will be saved."
   echo "f     The [f]rom language."
   echo "t     The [t]o language."
   echo "w     Whether to use or not data from OpenWebText."
   echo "c     Clean previous data processing."
   echo "l     Clean previous model."
   echo "v     Validation corpus size."
   echo
   echo "Example: ./preprocess_to_huggingface.sh -s 64000 -w no -m model_enlarged -v 20000 -f src -t trg -c true"
   echo
}

Progress_bar()
{
	total_sentences=$(wc -l < "$2")
	perc=0

  while [[ $perc -lt 100 ]]; do
		printf "\r\t\t$1 \t$perc%%"
		if [ -f  "$3" ]; then
			processed_sentences=$(wc -l < "$3")
			perc=$(echo "scale=2;$processed_sentences/$total_sentences*100" | bc -l)
			perc=${perc%.*}
		fi
	done
}

while getopts s:m:f:t:c:v:w: flag
do
    case "${flag}" in
        s) SPM_SIZE=${OPTARG};;
        m) MODEL=${OPTARG};;
        f) L1=${OPTARG};;
        t) L2=${OPTARG};;
        c) CLEAN=${OPTARG};;
        v) SIZE=${OPTARG};;
        w) USE_OWT=${OPTARG};;
        *) Usage
           exit;;
    esac
done

if (($# == 0))
then
    echo "No positional arguments specified"
    Usage
    exit 1
fi


if [ -z $SPM_SIZE ] || [ $SPM_SIZE -eq 0 ]
then
	echo "SPM vocab size was not specified or is zero."
	Usage
	exit 1
fi

if [ -z $MODEL ]
then
	echo "Model directory was not specified."
	Usage
	exit 1
fi

if [ -z $SIZE ]
then
	echo "Batch size was not specified."
	Usage
	exit 1
fi

if [ -z $CLEAN ]
then
	CLEAN=0
fi

echo "SPM vocab size set to: ${SPM_SIZE}"
echo "Model directory set to: ${MODEL}/"
echo "Soruce language l1: $L1. Target language l2: $L2"
echo "Erase all data set to: $CLEAN"
echo "Use OpenWebText data is set to : $USE_OWT"

if [ ! -e $MARIAN/build/marian ]
then
    echo "marian is not installed in $MARIAN/build, you need to compile the toolkit first"
    exit 1
fi

# check if moses-script is installed
if [ ! -e $MOSESSCRIPT_TOKENIZER ]
then
    echo "MOSES tokenizer is not installed in $MOSESSCRIPT_TOKENIZER, you need to install it first."
    exit 1
fi

if [ -d "$TEMP" ]
then
	rm -r $TEMP
fi

mkdir -p $TEMP

if [ $CLEAN = true ]
then
	# look for empty data directory
	if [ -d "$DATA" ]
	then
		# Build corpus
		echo -e "\tBuilding corpus..."
    if [ "$USE_OWT" == "yes" ]; then
		    java -jar -Xmx14g lib/grammar-1.0-SNAPSHOT.jar -b cbe -h $(pwd)
    else
        java -jar lib/grammar-1.0-SNAPSHOT.jar -b cb -h $(pwd)
    fi

		if [[ "$?" != '0' ]]; then
    			break
  	fi

		# create the corpus folder
		if [ -d $CORPUS ]
		then
    			echo "Erasing existing corpus"
    			rm -r $CORPUS
		fi

		mkdir -p $CORPUS

		if [ "$(ls -A $DATA)" ]; then
     			echo "Getting training corpus..."
     			mv "$DATA/$L1.gec.train" "$CORPUS/$CORPUS.train.$L1"
     			mv "$DATA/$L2.gec.train" "$CORPUS/$CORPUS.train.$L2"
     			mv "$DATA/$L1.gec.valid" "$CORPUS/$CORPUS.valid.$L1"
     			mv "$DATA/$L2.gec.valid" "$CORPUS/$CORPUS.valid.$L2"
		else
    			echo "$DATA is Empty."
    			exit 1

		fi
	else
		echo "Directory $DATA not found."
		exit 1
	fi

	# create the model folder
	if [ -d $MODEL ]
	then
    		echo "Erasing existing model"
    		rm -r $MODEL
	fi

	mkdir -p $MODEL

	for entry in corpus.train corpus.valid
	do
		for lang in $L1 $L2
		do
			echo -e "\tPreprocessing $entry.$lang"

			Progress_bar "replace-unicode-punctuation.perl" "$CORPUS/$entry.$lang" "$TEMP/$entry.unicode.$lang" &
			PID=$!
			cat "$CORPUS/$entry.$lang" | "${MOSESSCRIPT_TOKENIZER}/replace-unicode-punctuation.perl" > "$TEMP/$entry.unicode.$lang"
			kill $PID
			printf "\r\t\treplace-unicode-punctuation.perl \t100%%\n"

			Progress_bar "remove-non-printing-char.perl" "$TEMP/$entry.unicode.$lang" "$TEMP/$entry.printingchar.$lang" &
			PID=$!
			cat "$TEMP/$entry.unicode.$lang" | "${MOSESSCRIPT_TOKENIZER}/remove-non-printing-char.perl" > "$TEMP/$entry.printingchar.$lang"
			kill $PID
			printf "\r\t\tremove-non-printing-char.perl \t100%%\n"

			Progress_bar "normalize-punctuation.perl" "$TEMP/$entry.printingchar.$lang" "$TEMP/$entry.punctuation.$lang" &
			PID=$!
			cat "$TEMP/$entry.printingchar.$lang" | "${MOSESSCRIPT_TOKENIZER}/normalize-punctuation.perl" -l en > "$TEMP/$entry.punctuation.$lang"
			kill $PID
			printf "\r\t\trnormalize-punctuation.perl \t100%%\n"

			Progress_bar "sedding: cleaning multiple spaces" "$TEMP/$entry.unicode.$lang" "$TEMP/$entry.punctuation.$lang.out" &
			PID=$!
			sed 's/  */ /g;s/^ *//g;s/ *$$//g' "$TEMP/$entry.punctuation.$lang" > "$TEMP/$entry.punctuation.$lang.out"
			kill $PID
			printf "\r\t\tsedding: cleaning multiple spaces \t100%%\n"
			mv "$TEMP/$entry.punctuation.$lang.out" "$TEMP/$entry.punctuation.$lang"

			Progress_bar "tokenizer.perl" $TEMP/$entry.punctuation.$lang $CORPUS/$entry.tok.$lang &
			PID=$!
			cat $TEMP/$entry.punctuation.$lang | ${MOSESSCRIPT_TOKENIZER}/tokenizer.perl -q -l en > $CORPUS/$entry.tok.$lang
			kill $PID
			printf "\r\t\ttokenizer.perl \t100%%\n"
		done
	done

	for lang in $L1 $L2
	do
		echo -e "\tTraining spm model for $lang"
		$MARIAN/build/spm_train --input "$CORPUS/$CORPUS.train.tok.$lang" --model_prefix=$MODEL/$lang --bos_id=-1 --eos_id=0 --unk_id=1 --vocab_size=$SPM_SIZE --normalization_rule_name=nmt_nfkc \
		--character_coverage=1.0 --model_type=$SPM_TYPE --train_extremely_large_corpus=true --input_sentence_size=10000000
    #--shuffle_input_sentence=true

		echo -e "\tExporting vocab for $lang"
		$MARIAN/build/spm_export_vocab --output "$MODEL/vocab.$lang.txt" --output_format "syms" --model "$MODEL/$lang.model"

		# Convert from .model to .spm because this is the file extension required by Hugginface's model converter
		mv "$MODEL/$lang.model" "$MODEL/$lang.spm"

		Progress_bar "Encoding training corpus $lang" "$CORPUS/$CORPUS.train.tok.$lang" "$CORPUS/corpus.train.encoded.$lang" &
		PID=$!
		cat "$CORPUS/$CORPUS.train.tok.$lang" | $MARIAN/build/spm_encode --model "$MODEL/$lang.spm" > "$CORPUS/corpus.train.encoded.$lang"
		kill $PID
		printf "\r\t\tEncoding training corpus $lang \t100%%\n"

		Progress_bar "Encoding validation corpus $lang" "$CORPUS/$CORPUS.valid.tok.$lang" "$CORPUS/corpus.valid.encoded.$lang" &
		PID=$!
		cat "$CORPUS/$CORPUS.valid.tok.$lang" | $MARIAN/build/spm_encode --model "$MODEL/$lang.spm" > "$CORPUS/corpus.valid.encoded.$lang"
		kill $PID
		printf "\r\t\tEncoding validation corpus $lang \t100%%\n"
	done

	# Due to poor model normalization paramater tuning, best translation can be empty
	# So here lines with length < 1 (empty) are deleted. See https://github.com/marian-nmt/marian-dev/issues/462
	Progress_bar "Cleaning training files: " "$CORPUS/corpus.train.encoded.$L1" "$CORPUS/corpus.train.encoded.$L1.tmp" &
	PID=$!
	java FileCleaner.java "$CORPUS/corpus.train.encoded.$L1" "$CORPUS/corpus.train.encoded.$L2"
	if [ -n "${PID}" ]; then
		kill $PID
	fi
	printf "\r\t\tCleaning training files:  \t100%%\n"

	Progress_bar "Cleaning validation files: " "$CORPUS/corpus.valid.encoded.$L1" "$CORPUS/corpus.valid.encoded.$L1.tmp" &
	PID=$!
	java FileCleaner.java "$CORPUS/corpus.valid.encoded.$L1" "$CORPUS/corpus.valid.encoded.$L2"
	if [ -n "${PID}" ]; then
		kill $PID
	fi
	printf "\r\t\tCleaning validation files: \t100%%\n"

	# Reduce size of validation corpus
	python random_lines_gettr.py --source "$CORPUS/corpus.valid.encoded.$L1" --target "$CORPUS/corpus.valid.encoded.$L2" --length $SIZE

	# build vocabulary as vocab.yml
	echo "Building vocabulary"
	python build_vocab.py $MODEL

fi

rm -r $TEMP

echo "The End!"
