package ca.baosiek;

import ca.baosiek.utils.ShellExecutor;
import ca.baosiek.utils.ToConsole;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CorpusBuilder {

    private final String HOME_DIR;
    private final File TEMP_DIR;

    public CorpusBuilder(String homeDir){
        this.HOME_DIR = homeDir;
        this.TEMP_DIR = Path.of(HOME_DIR, "temp_java").toFile();
        boolean del = false;
        if (this.TEMP_DIR.exists()){
            del = this.TEMP_DIR.delete();
        }
        this.TEMP_DIR.mkdir();
    }

    public boolean join(int subLevel){

        ShellExecutor.setDirectory(HOME_DIR);
        ShellExecutor.exec(String.format("paste %s %s > %s", "data/clang8/clang8.src.train.txt", "data/clang8/clang8.trg.train.txt", "temp_java/gec.train"));
        ShellExecutor.exec(String.format("paste %s %s > %s", "data/clang8/clang8.src.valid.txt", "data/clang8/clang8.trg.valid.txt", "temp_java/gec.valid"));

        Integer joined = Integer.parseInt(ShellExecutor.exec(String.format("wc -l %s | awk '{print $1}'", "temp_java/gec.train")).replace("\n", ""));
        Integer original = Integer.parseInt(ShellExecutor.exec(String.format("wc -l %s | awk '{print $1}'", "data/clang8/clang8.src.train.txt")).replace("\n", ""));

        ToConsole.printLine(String.format("data/clang8/clang8.src.train.txt: [%,d]\ttemp_java/gec.train: [%,d]", original, joined), subLevel);
        assertEquals(original, joined, "Original train file size and joined file size must be equal");

        joined = Integer.parseInt(ShellExecutor.exec(String.format("wc -l %s | awk '{print $1}'", "temp_java/gec.valid")).replace("\n", ""));
        original = Integer.parseInt(ShellExecutor.exec(String.format("wc -l %s | awk '{print $1}'", "data/clang8/clang8.src.valid.txt")).replace("\n", ""));

        ToConsole.printLine(String.format("data/clang8/clang8.src.valid.txt: [%,d]\ttemp_java/gec.valid: [%,d]", original, joined), subLevel);
        assertEquals(original, joined, "Original validation file size and joined file size must be equal");
        return true;
    }

    public boolean shuffle(final int subLevel) {

        String[] files = {"train", "valid"};
        AtomicBoolean result = new AtomicBoolean(false);
        Arrays.stream(files).forEach(f -> {
            try {
                Path trainFile = Path.of(HOME_DIR, "temp_java", String.format("gec.%s", f));
                BufferedReader trainFileReader = Files.newBufferedReader(trainFile);
                List<String> shuffleArray = trainFileReader.lines().collect(Collectors.toList());
                trainFileReader.close();

                Collections.shuffle(shuffleArray);

                BufferedWriter trainFileWriter = Files.newBufferedWriter(trainFile);
                shuffleArray.stream().forEach(l -> {
                    try {
                        trainFileWriter.append(l).append("\n");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                trainFileWriter.close();
                Integer size = Integer.parseInt(ShellExecutor.exec(
                        String.format("wc -l temp_java/gec.%s | awk '{print $1}'", f)).replace("\n", ""));
                ToConsole.printLine(String.format("Shuffled file [temp_java/gec.%s] has [%,d] lines", f, size), subLevel);
                result.set(true);
            } catch (IOException ex) {
                ex.printStackTrace();
                result.set(false);
            }
        });
        return result.get();
    }

    public boolean backToCorpus(final int subLevel){

        boolean processed = false;
        String[] files = {"train", "valid"};
        for (String file : files) {
            try {
                Path trgTrainFile = Path.of(this.HOME_DIR, "temp_java", String.format("gec.%s", file));
                BufferedReader trgTrainBr = Files.newBufferedReader(trgTrainFile);

                Path src = Path.of(this.HOME_DIR, "data", String.format("src.gec.%s", file));
                BufferedWriter srcTempBw = Files.newBufferedWriter(src);

                Path trg = Path.of(this.HOME_DIR, "data", String.format("trg.gec.%s", file));
                BufferedWriter trgTempBw = Files.newBufferedWriter(trg);

                AtomicInteger counter = new AtomicInteger(0);
                trgTrainBr.lines().forEach(line -> {
                    String[] lineParts = line.split("\t");
                    if (lineParts.length != 2){
                        String.format("LINE PARTS: [%s]", line);
                    }
                    try {
                        srcTempBw.write(lineParts[0].trim());
                        srcTempBw.newLine();
                        trgTempBw.write(lineParts[1].trim());
                        trgTempBw.newLine();
                        ToConsole.print(String.format("Processed: [%,d]", counter.addAndGet(1)), subLevel);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                });
                ToConsole.printLine("", 0);

                trgTrainBr.close();
                srcTempBw.close();
                trgTempBw.close();

                ShellExecutor.setDirectory(HOME_DIR);
                Integer source = Integer.parseInt(ShellExecutor.exec(String.format("wc -l data/src.gec.%s | awk '{print $1}'", file)).replace("\n", ""));
                Integer target = Integer.parseInt(ShellExecutor.exec(String.format("wc -l data/trg.gec.%s | awk '{print $1}'", file)).replace("\n", ""));

                ToConsole.printLine(String.format("data/src.gec.%s: [%,d]\tdata/trg.gec.%s: [%,d]", file, source, file, target), subLevel);
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

    public void process(final int subLevel){
        // 1. Join src and trg from clang8ls mode
        ToConsole.printLine("Joining...", subLevel);
        boolean joined = this.join(subLevel+1);
        if (!joined) {
            System.exit(1);
        }

        // 2. Shuffle src and trg joined
        ToConsole.printLine("Shuffling...", subLevel);
        boolean shuffled = this.shuffle(subLevel+1);
        if (!shuffled) {
            System.exit(1);
        }

        // 3. Split and send src and trg files to corpus
        ToConsole.printLine("Backing to corpus...", subLevel);
        boolean concluded = this.backToCorpus(subLevel+1);
        if (!concluded) {
            System.exit(1);
        }
    }
}
