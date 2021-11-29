package ca.baosiek;

import ca.baosiek.utils.ShellExecutor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CorpusBuilderModelEnlarged {

    private final String HOME_DIR;
    private final File TEMP_DIR;
    private final String TAB = "\t";
    private final String LF_CR = "\n";
    private final String CR = "\r";

    public CorpusBuilderModelEnlarged(String dir) {
        this.HOME_DIR = dir;
        String TEMP_PATH = HOME_DIR + "/temp_java/";
        this.TEMP_DIR = new File(TEMP_PATH);
    }

    private void print(String message, int level) {
        System.out.printf(TAB.repeat(level).concat(message).concat(LF_CR));
    }

    private void monitor(String message, int level) {
        System.out.printf(TAB.repeat(level).concat(message).concat(CR));
    }

    public int[] getMeanStd() {

        /*
         * Text to train will consist of content from Openwebtext whose text length in tokens is greater than length
         * mean +- 2 std.
         */
        print("Computing statistics...", 1);
        DescriptiveStatistics lengths = new DescriptiveStatistics();

        // To store returned array
        int[] range = new int[2];

        try {
            Path openWeb = Path.of(this.HOME_DIR, "data", "openwebtext", "owt.ref.train.txt");
            BufferedReader br = Files.newBufferedReader(openWeb);

            // Tokenize only on white space
            br.lines().parallel().forEach(line -> lengths.addValue(line.split(" ").length));

            //Print statistics
            print(String.format("Number of lines: [%,d]", lengths.getN()), 2);
            double mean = lengths.getMean();
            print(String.format("Mean: [%,.2f]", mean), 2);
            double std = lengths.getStandardDeviation();
            print(String.format("Std: [%,.2f]", std), 2);
            print(String.format("Min: %,.0f", lengths.getMin()), 2);
            print(String.format("Max: %,.0f", lengths.getMax()), 2);

            int lower = (int) (mean - 2 * std);

            range[0] = Math.max(lower, 2);

            range[1] = (int) (mean + 2 * std);
            print(String.format("Computed length range: [%,d, %,d]", range[0], range[1]), 2);

        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return range;
    }

    public boolean filterOpenWebText(int[] range) {
        print("Filtering Openwebtext...", 1);
        if (this.TEMP_DIR.exists()) {
            if (this.TEMP_DIR.delete()) System.out.printf("\t%s was deleted\n", this.TEMP_DIR);
        }
        this.TEMP_DIR.mkdirs();

        boolean result;
        try {
            Path fileRef = Path.of(this.HOME_DIR, "data", "openwebtext", "owt.ref.train.txt");
            BufferedReader brRef = Files.newBufferedReader(fileRef);

            Path fileSrc = Path.of(this.HOME_DIR, "data", "openwebtext", "owt.src.train.txt");
            BufferedReader brSrc = Files.newBufferedReader(fileSrc);

            Path newFileRef = Path.of(this.HOME_DIR, this.TEMP_DIR.getName(), "ref.gec.train");
            BufferedWriter bwRef = Files.newBufferedWriter(newFileRef);

            Path newFileSrc = Path.of(this.HOME_DIR, this.TEMP_DIR.getName(), "src.gec.train");
            BufferedWriter bwSrc = Files.newBufferedWriter(newFileSrc);

            String lineRef, lineSrc;
            while ((lineRef = brRef.readLine()) != null && (lineSrc = brSrc.readLine()) != null) {
                int lineLength = lineRef.split(" ").length;
                if (lineLength >= range[0] && lineLength <= range[1]) {
                    bwRef.write(lineRef);
                    bwRef.newLine();

                    bwSrc.write(lineSrc);
                    bwSrc.newLine();
                }
            }
            brRef.close();
            bwRef.close();
            brSrc.close();
            bwSrc.close();
            result = true;
        } catch (IOException ex) {
            ex.printStackTrace();
            result = false;
        }

        ShellExecutor.setDirectory(HOME_DIR);
        Double original = Double.parseDouble(ShellExecutor.exec(
                        String.format("wc -l data/openwebtext/%s | awk '{print $1}'", "owt.ref.train.txt"))
                .replace("\n", ""));

        Double srcSize = Double.parseDouble(ShellExecutor.exec(
                        String.format("wc -l %s | awk '{print $1}'", this.TEMP_DIR.getName() + "/src.gec.train"))
                .replace("\n", ""));
        Double refSize = Double.parseDouble(ShellExecutor.exec(
                        String.format("wc -l %s | awk '{print $1}'", this.TEMP_DIR.getName() + "/ref.gec.train"))
                .replace("\n", ""));

        assertEquals(refSize, srcSize, "Sizes must be the same");
        print(String.format("Filtered size: [%,.0f]", refSize), 2);
        print(String.format("Original size: [%,.0f]", original), 2);
        print(String.format("Percent size: [%,.2f]", refSize / original), 2);

        return result;
    }

    public boolean merge() {

        print("Merging...", 1);
        ShellExecutor.setDirectory(HOME_DIR);
        ShellExecutor.exec(String.format("cat %s >> %s", "data/clang8/clang8.src.train.txt", "temp_java/src.gec.train"));
        ShellExecutor.exec(String.format("cat %s >> %s", "data/clang8/clang8.ref.train.txt", "temp_java/ref.gec.train"));

        Integer source = Integer.parseInt(ShellExecutor.exec(
                        String.format("wc -l %s | awk '{print $1}'", "temp_java/src.gec.train"))
                .replace("\n", ""));

        Integer target = Integer.parseInt(ShellExecutor.exec(
                        String.format("wc -l %s | awk '{print $1}'", "temp_java/ref.gec.train"))
                .replace("\n", ""));

        print(String.format("temp_java/src.gec.train: [%,d]\ttemp_java/ref.gec.train: [%,d]", source, target), 2);

        assertEquals(source, target, "Source and target training files sizes must be equal");

        ShellExecutor.exec(String.format("cat %s > %s", "data/openwebtext/owt.src.valid.txt", "temp_java/src.gec.valid"));
        ShellExecutor.exec(String.format("cat %s > %s", "data/openwebtext/owt.ref.valid.txt", "temp_java/ref.gec.valid"));
        ShellExecutor.exec(String.format("cat %s >> %s", "data/clang8/clang8.src.valid.txt", "temp_java/src.gec.valid"));
        ShellExecutor.exec(String.format("cat %s >> %s", "data/clang8/clang8.ref.valid.txt", "temp_java/ref.gec.valid"));

        source = Integer.parseInt(ShellExecutor.exec(
                        String.format("wc -l %s | awk '{print $1}'", "temp_java/src.gec.valid"))
                .replace("\n", ""));
        target = Integer.parseInt(ShellExecutor.exec(
                        String.format("wc -l %s | awk '{print $1}'", "temp_java/ref.gec.valid"))
                .replace("\n", ""));

        print(String.format("temp_java/src.gec.valid: [%,d]\ttemp_java/ref.gec.valid: [%,d]", source, target), 2);

        assertEquals(source, target, "Source and target validation files sizes must be equal");

        return true;
    }

    public boolean join() {

        print("Joining...", 1);
        ShellExecutor.setDirectory(HOME_DIR);
        ShellExecutor.exec(String.format("paste %s %s > %s", "temp_java/src.gec.train", "temp_java/ref.gec.train", "temp_java/gec.train"));
        ShellExecutor.exec(String.format("paste %s %s > %s", "temp_java/src.gec.valid", "temp_java/ref.gec.valid", "temp_java/gec.valid"));
        Integer joined = Integer.parseInt(ShellExecutor.exec(String.format("wc -l %s | awk '{print $1}'", "temp_java/gec.train")).replace("\n", ""));
        Integer merged = Integer.parseInt(ShellExecutor.exec(String.format("wc -l %s | awk '{print $1}'", "temp_java/src.gec.train")).replace("\n", ""));

        print(String.format("temp_java/src.gec.train: [%,d]\ttemp_java/gec.train: [%,d]", merged, joined), 2);
        assertEquals(merged, joined, "Merged train file size and joined file size must be equal");

        joined = Integer.parseInt(ShellExecutor.exec(String.format("wc -l %s | awk '{print $1}'", "temp_java/gec.valid")).replace("\n", ""));
        merged = Integer.parseInt(ShellExecutor.exec(String.format("wc -l %s | awk '{print $1}'", "temp_java/src.gec.valid")).replace("\n", ""));

        print(String.format("temp_java/src.gec.valid: [%,d]\ttemp_java/gec.valid: [%,d]", merged, joined), 2);
        assertEquals(merged, joined, "Merged train file size and joined file size must be equal");

        return true;
    }

    public boolean shuffle() {

        print("Shuffling...", 1);
        String[] files = {"train", "valid"};
        AtomicBoolean result = new AtomicBoolean(false);
        AtomicBoolean proceed = new AtomicBoolean(true);
        Arrays.stream(files).forEach(f -> {
            try {
                Path trainFile = Path.of(TEMP_DIR.getPath(), String.format("gec.%s", f));
                BufferedReader trainFileReader = Files.newBufferedReader(trainFile);

                long heapMaxSize = Runtime.getRuntime().maxMemory();
                long heapFreeSize = Runtime.getRuntime().freeMemory();
                long fileSize = Files.size(trainFile);

                if (heapMaxSize - heapFreeSize < fileSize) {
                    print(String.format("File size is: %,d MB", fileSize / 1024 / 1024), 2);
                    print(String.format("Available heap size is: %,d MB", (heapMaxSize - heapFreeSize) / 1024 / 1024), 2);
                    print(String.format("Max heap size is: %,d MB", heapMaxSize / 1024 / 1024), 2);
                    print("Insufficient max heap size to shuffle, thus aborting this procedure.", 2);
                    proceed.set(false);
                }

                if (!proceed.get()) return;

                List<String> shuffleArray = new ArrayList<>();
                String line;
                long counter = 0;
                while ((line = trainFileReader.readLine()) != null) {
                    shuffleArray.add(line);
                    monitor(String.format("Counting temp_java/gec.%s: %,d", f, ++counter), 2);
                }
                print("", 0);
                trainFileReader.close();

                Collections.shuffle(shuffleArray);

                BufferedWriter trainFileWriter = Files.newBufferedWriter(trainFile);
                shuffleArray.forEach(l -> {
                    try {
                        trainFileWriter.write(l);
                        trainFileWriter.newLine();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                trainFileWriter.close();
                Integer size = Integer.parseInt(ShellExecutor.exec(
                        String.format("wc -l temp_java/gec.%s | awk '{print $1}'", f)).replace("\n", ""));
                print(String.format("Shuffled file [temp_java/gec.%s] has [%d] lines", f, size), 2);
                result.set(true);
            } catch (IOException ex) {
                ex.printStackTrace();
                result.set(false);
            }
        });

        if (!proceed.get()) return true;
        return result.get();
    }

    public boolean backToCorpus() {

       print("Backing to corpus...", 1);
        boolean processed = false;
        String[] files = {"train", "valid"};
        for (String file : files) {
            try {
                Path refTrainFile = Path.of(TEMP_DIR.getPath(), String.format("gec.%s", file));
                BufferedReader refTrainBr = Files.newBufferedReader(refTrainFile);

                Path src = Path.of(HOME_DIR, "data", String.format("src.gec.%s", file));
                BufferedWriter srcTempBw = Files.newBufferedWriter(src);

                Path ref = Path.of(HOME_DIR, "data", String.format("ref.gec.%s", file));
                BufferedWriter refTempBw = Files.newBufferedWriter(ref);

                AtomicInteger counter = new AtomicInteger(0);
                refTrainBr.lines().parallel().forEach(line -> {
                    String[] lineParts = line.split("\t");
                    if (lineParts.length != 2) {
                        System.out.printf("LINE PARTS: [%s]", line);
                    }
                    try {
                        srcTempBw.write(lineParts[0].trim());
                        srcTempBw.newLine();
                        refTempBw.write(lineParts[1].trim());
                        refTempBw.newLine();
                        monitor(String.format("Processed: [%,d]", counter.addAndGet(1)), 2);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                });
                print("", 0);

                refTrainBr.close();
                srcTempBw.close();
                refTempBw.close();

                ShellExecutor.setDirectory(HOME_DIR);
                Integer source = Integer.parseInt(ShellExecutor.exec(String.format("wc -l data/src.gec.%s | awk '{print $1}'", file)).replace("\n", ""));
                Integer target = Integer.parseInt(ShellExecutor.exec(String.format("wc -l data/ref.gec.%s | awk '{print $1}'", file)).replace("\n", ""));

                print(String.format("data/src.gec.%s: [%,d]\tdata/ref.gec.%s: [%,d]", file, source, file, target), 2);
                assertEquals(source, target, "Source and target files must have the same size");
                processed = true;

            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }

        try {
            FileUtils.deleteDirectory(this.TEMP_DIR);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return processed;
    }

    public void process() {
        // 1. Get range of sentence length in tokens
        int[] range = this.getMeanStd();

        // 2. Filter Openwebtext to contain sentences within range
        boolean filtered = this.filterOpenWebText(range);
        if (!filtered) {
            System.exit(1);
        }

        // 3. Merge src and ref from openwebtext and sentences
        boolean merged = this.merge();
        if (!merged) {
            System.exit(1);
        }

        // 4. Join src and ref from openwebtext and sentences
        boolean joined = this.join();
        if (!joined) {
            System.exit(1);
        }

        // 4. Shuffle src and ref joined
        boolean shuffled = this.shuffle();
        if (!shuffled) {
            System.exit(1);
        }

        // 5. Split and send src and ref files to raw
        boolean concluded = this.backToCorpus();
        if (!concluded) {
            System.exit(1);
        }

        System.out.println("The End for CorpusBuilder!");

    }

    public static void main(String[] args) {
        System.out.println("Starting CorpusBuilder...");
        CorpusBuilderModelEnlarged cb = new CorpusBuilderModelEnlarged("/home/baosiek/Development/marian/examples/transformer/");
        cb.process();
    }
}
