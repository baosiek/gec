#!/bin/bash

MARIAN=../..
TOOLS=$MARIAN/tools
MOSESSCRIPT_TOKENIZER=$TOOLS/moses-scripts/scripts/tokenizer
CORPUS=corpus
DATA=/home/baosiek/Development/marian/examples/transformer/raw
HUGGINGFACE_DIR=/home/baosiek/Development/dlpt/gec/en_src-en_ref
L1=src # stands for not english
L2=trg # stands for english
SPM_TYPE=unigram
TEMP=temp
EARLY_STOP=20

Usage()
{
   # Display Help
   echo
   echo "USAGE"
   echo "Syntax: run-to-huggingface [-g|m|f|t]"
   echo "options:"
   echo "g     Assign GPU number."
   echo "q     Batch (not mini batch) size."
   echo "m     Directory where the model will be saved."
   echo "f     The [f]rom language."
   echo "t     The [t]o language."
   echo "c     Clean previous data processing."
   echo "l     Clean previous model."
   echo
   echo "Example: ./train_to_huggingface.sh -g 0 -m model_enlarged -q 3000000 -f src -t trg"
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

while getopts g:s:m:f:t:c:q: flag
do
    case "${flag}" in
    	g) GPUS=${OPTARG};;
        s) SPM_SIZE=${OPTARG};;
        m) MODEL=${OPTARG};;
        f) L1=${OPTARG};;
        t) L2=${OPTARG};;
        c) CLEAN=${OPTARG};;
        q) QTY=${OPTARG};;
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

if [ -z $MODEL ]
then
	echo "Model directory was not specified."
	Usage
	exit 1
fi

if [ -z $QTY ]
then
	echo "Batch size was not set."
	Usage
	exit 1
fi

echo "Model directory set to: ${MODEL}/"
echo "Soruce language l1: $L1. Target language l2: $L2"

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

proceed="y"
while [ "$proceed" == "y" ]
do
	read -p "Proceed? [y/N] : " proceed
	if [ -z "$proceed" ] || [ "$proceed" != "y" ] && [ "$proceed" != "yes" ] && [ "$proceed" != "Yes" ]; then
		echo "Stopping..."
	else
		echo "Proceeding..."
		java -jar lib/grammar-1.0-SNAPSHOT.jar -h $(pwd)/ -b bb -q $QTY
		if [[ "$?" == '1' ]]; then
    			break
  		fi

		if [ -d "$TEMP" ]
		then
			rm -r $TEMP
		fi

		mkdir -p $TEMP

		# aligning corpus training data
		echo "Aligning corpus"
		paste $CORPUS/corpus.train.encoded.batch.$L1 $CORPUS/corpus.train.encoded.batch.$L2 > $TEMP/align.$L1-$L2
		echo -e "\tSedding [\\t/ ||| ]"
		sed -i 's/\t/ ||| /g' $TEMP/align.$L1-$L2
		echo -e "\tForward aligning..."
		$TOOLS/fast_align/build/fast_align -vdo -i $TEMP/align.$L1-$L2 > $TEMP/forward.align.$L1-$L2
    echo -e "\tReverse aligning..."
		$TOOLS/fast_align/build/fast_align -vdor -i $TEMP/align.$L1-$L2 > $TEMP/reverse.align.$L1-$L2
    echo -e "\tFast aligning..."
		Progress_bar "Fast aligning" $TEMP/forward.align.$L1-$L2 $CORPUS/corpus.aligned.$L1-$L2 &
		PID=$!
		$TOOLS/fast_align/build/atools -c grow-diag-final -i $TEMP/forward.align.$L1-$L2 -j $TEMP/reverse.align.$L1-$L2 > $CORPUS/corpus.aligned.$L1-$L2
		if [ -n "${PID}" ]; then
			kill $PID
		fi
		printf "\r\t\tFast aligning \t100%%\n"

		rm -r $TEMP

		# get vocab.yml size
		VOCAB_SIZE=$(wc -l $MODEL/vocab.yml | awk '{print $1;}')
		echo "Vocab size is: [$VOCAB_SIZE]"

    # Increase early stop to enable to continue training for additional 5 early stops
    # as recomended at https://github.com/marian-nmt/marian/issues/224
    if [ -f $MODEL/model.npz.yml ]; then
      EARLY_STOP=$(grep "early-stopping" $MODEL/model.npz.yml | awk '{print $2;}')
      EARLY_STOP=$((EARLY_STOP + 5))
    fi
    echo "Early stop set to: $EARLY_STOP stalls"

		# train model
		$MARIAN/build/marian \
    			--devices $GPUS \
    			--type transformer \
    			--model $MODEL/model.npz \
    			--train-sets $CORPUS/corpus.train.encoded.batch.$L1 $CORPUS/corpus.train.encoded.batch.$L2 \
    			--vocabs $MODEL/vocab.yml $MODEL/vocab.yml \
    			--dim-vocabs $VOCAB_SIZE $VOCAB_SIZE --max-length 100 \
    			--guided-alignment $CORPUS/corpus.aligned.$L1-$L2 \
    			--mini-batch 16 --valid-mini-batch 8 -w 4000 \
    			--transformer-postprocess-emb d \
    			--transformer-postprocess dan \
    			--transformer-dropout 0.1 --label-smoothing 0.1 \
    			--early-stopping $EARLY_STOP \
    			--tied-embeddings-all --sync-sgd \
    			--valid-freq 100000 --save-freq 100000 --disp-freq 10000 \
    			--cost-type ce-mean-words --valid-metrics ce-mean-words bleu-detok \
    			--valid-sets $CORPUS/corpus.valid.encoded.$L1 $CORPUS/corpus.valid.encoded.$L2 \
    			--log $MODEL/train.log --valid-log $MODEL/valid.log --tempdir $MODEL/model \
    			--overwrite --keep-best --shuffle data \
    			--seed 1111 --exponential-smoothing \
    			--enc-depth 6 --dec-depth 6 --transformer-heads 8 \
    			--normalize 0.6 --beam-size 6 --quiet-translation \
          --lr-warmup 32000 --lr-decay-inv-sqrt 32000 \
    			--learn-rate 0.00003 --lr-report \
    			--optimizer-params 0.9 0.98 1e-09 --clip-norm 5 \
          #--no-restore-corpus #--check-gradient-nan \
	fi
done

echo "The End!"
