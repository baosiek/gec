package ca.baosiek;

import ca.baosiek.utils.ShellExecutor;

import java.io.*;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class BatchBuilder {

    private final String HOME_DIR;
    private final String RAW = "raw";

    public BatchBuilder(String dir){
        this.HOME_DIR = dir;
    }

    public String getHOME_DIR(){
        return this.HOME_DIR;
    }

    public List<Integer> generateRandomNumbers(int quantity, int upperLimit) {

        if (quantity > upperLimit){
            System.err.println(String.format("Quantity %d is greater than upperLimit %d", quantity, upperLimit));
        }

//      Generating 'quantity' random numbers in the range from 1 to upperLimit included
        List<Integer> range = IntStream.range(1, upperLimit + 1).boxed()
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(range);
        range = range.subList(0, quantity).stream().sorted(Integer::compareTo).collect(Collectors.toList());

        return new ArrayList<>(range);
    }

    private void buildBatches(List<Integer> numbers) throws IOException {

        System.out.println("\tBuilding batch...");
        File sourceTrainEncodedFile = new File(HOME_DIR + "corpus/corpus.train.encoded.src");
        BufferedReader sourceTrainEncodedReader = new BufferedReader(new FileReader(sourceTrainEncodedFile));

        File targetTrainEncodedFile = new File(HOME_DIR + "corpus/corpus.train.encoded.trg");
        BufferedReader targetTrainEncodedReader = new BufferedReader(new FileReader(targetTrainEncodedFile));

        File sourceTempFile = new File(HOME_DIR + "corpus/corpus.train.encoded.temp.src");
        BufferedWriter sourceTempWriter = new BufferedWriter(new FileWriter(sourceTempFile));

        File targetTempFile = new File(HOME_DIR + "corpus/corpus.train.encoded.temp.trg");
        BufferedWriter targetTempWriter = new BufferedWriter(new FileWriter(targetTempFile));

        File sourceBatchFile = new File(HOME_DIR + "corpus/corpus.train.encoded.batch.src");
        BufferedWriter sourceBatchWriter = new BufferedWriter(new FileWriter(sourceBatchFile));

        File targetBatchFile = new File(HOME_DIR + "corpus/corpus.train.encoded.batch.trg");
        BufferedWriter targetBatchWriter = new BufferedWriter(new FileWriter(targetBatchFile));


        String srcLine, refLine;
        int index = 0;
        int pointer = 0;
        int number = numbers.get(index);
        while ((srcLine = sourceTrainEncodedReader.readLine()) != null && (refLine = targetTrainEncodedReader.readLine()) != null) {

            if (++pointer == number) {
                sourceBatchWriter.write(srcLine);
                sourceBatchWriter.newLine();

                targetBatchWriter.write(refLine);
                targetBatchWriter.newLine();

                if (++index < numbers.size()) {
                    number = numbers.get(index);
                }
            } else {
                sourceTempWriter.write(srcLine);
                sourceTempWriter.newLine();

                targetTempWriter.write(refLine);
                targetTempWriter.newLine();
            }
            System.out.printf("\tNumber of lines processed: %d\r", pointer + 1);
        }
        System.out.println();

        sourceTempFile.renameTo(sourceTrainEncodedFile);
        targetTempFile.renameTo(targetTrainEncodedFile);

        sourceTrainEncodedReader.close();
        targetTrainEncodedReader.close();
        sourceTempWriter.close();
        targetTempWriter.close();
        sourceBatchWriter.close();
        targetBatchWriter.close();
    }

    public void process(Integer quantity){
        ShellExecutor.setDirectory(this.getHOME_DIR());
        Integer upperLimit = Integer.parseInt(ShellExecutor.exec(String.format("wc -l %s | awk '{print $1}'", "corpus/corpus.train.encoded.src")).replace("\n", ""));

        if (upperLimit < 2){
            System.out.println("\tNo more text to generate batch...");
            System.exit(1);
        }

        System.out.printf("\tQuantity: %d, Upper Limit: %d\n", quantity, upperLimit);
        if (quantity > upperLimit){
            quantity = upperLimit;
            System.out.printf("\tQuantity set to : %d\n", quantity);
        }

        List<Integer> randNumbers = this.generateRandomNumbers(quantity, upperLimit);
        System.out.printf("\tBatch will contain: %d lines\n", randNumbers.size());

        try {
            this.buildBatches(randNumbers);
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("\tThe End BatchBuilder!");
    }

    public static void main(String[] args) {

        BatchBuilder batchBuilder = new BatchBuilder("/home/baosiek/Development/marian/examples/transformer/");
        Integer quantity = Integer.parseInt(args[0]);
        batchBuilder.process(quantity);
    }
}
