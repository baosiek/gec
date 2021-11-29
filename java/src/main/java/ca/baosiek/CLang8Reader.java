package ca.baosiek;

import ca.baosiek.utils.CLang8Enum;
import ca.baosiek.utils.ShellExecutor;
import ca.baosiek.utils.ToConsole;
import ca.baosiek.utils.UnicodePunctuationNormalizer;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CLang8Reader {

    private final String homeDir;
    private final JSONObject clang8Config;
    private BufferedWriter srcTrainWriter;
    private BufferedWriter trgTrainWriter;
    private BufferedWriter srcValidWriter;
    private BufferedWriter trgValidWriter;
    private BufferedWriter srcTestWriter;
    private BufferedWriter trgTestWriter;
    private Map<Integer, String> randomIndexes;
    private AtomicInteger counter;
    private final int LEVEL;

    public CLang8Reader(String homeDir) {
        this.homeDir = homeDir;
        Path clang8ConfigPath = Path.of(this.homeDir, "config", "clang8.json");
        clang8Config = loadConfig(clang8ConfigPath);
        LEVEL = 1;
    }

    private void assertFileSizes() throws IOException {

        String dataDir = (String) clang8Config.get(CLang8Enum.DATA_DIR.name());

        // Get the number of instances of clang8
        ShellExecutor.setDirectory(dataDir);
        Integer cLang8SrcTrainSize = Integer.parseInt(ShellExecutor.exec(String.format("wc -l %s | awk '{print $1}'", (String) clang8Config.get(CLang8Enum.C_LANG_8_SRC_TRAIN.name()))).replace("\n", ""));
        Integer cLang8trgTrainSize = Integer.parseInt(ShellExecutor.exec(String.format("wc -l %s | awk '{print $1}'", (String) clang8Config.get(CLang8Enum.C_LANG_8_TRG_TRAIN.name()))).replace("\n", ""));
        assertEquals(cLang8SrcTrainSize, cLang8trgTrainSize, "Files for train must have the same size");

        Integer cLang8SrcValidSize = Integer.parseInt(ShellExecutor.exec(String.format("wc -l %s | awk '{print $1}'", (String) clang8Config.get(CLang8Enum.C_LANG_8_SRC_VALID.name()))).replace("\n", ""));
        Integer cLang8trgValidSize = Integer.parseInt(ShellExecutor.exec(String.format("wc -l %s | awk '{print $1}'", (String) clang8Config.get(CLang8Enum.C_LANG_8_TRG_VALID.name()))).replace("\n", ""));
        assertEquals(cLang8SrcValidSize, cLang8trgValidSize, "Files for validation must have the same size");

        Integer cLang8SrcTestSize = Integer.parseInt(ShellExecutor.exec(String.format("wc -l %s | awk '{print $1}'", (String) clang8Config.get(CLang8Enum.C_LANG_8_SRC_TEST.name()))).replace("\n", ""));
        Integer cLang8trgTestSize = Integer.parseInt(ShellExecutor.exec(String.format("wc -l %s | awk '{print $1}'", (String) clang8Config.get(CLang8Enum.C_LANG_8_TRG_TEST.name()))).replace("\n", ""));
        assertEquals(cLang8SrcTestSize, cLang8trgTestSize, "Files for testing must have the same size");
    }

    private Map<Integer, String> getIndexes(Integer cLang8Size) {

        List<Integer> randList = IntStream.range(0, cLang8Size).boxed().collect(Collectors.toList());
        Collections.shuffle(randList);

        // First 20.000 instances to validation. Next 20.000 instances to testing and
        // remaining instances to training
        Map<Integer, String> indexes = new HashMap<>();
        AtomicInteger count = new AtomicInteger(0);
        randList.forEach(r -> {
            if (count.getAndAdd(1) < 20000) {
                indexes.put(r, "valid");
            } else if (count.get() >= 20000 && count.get() < 40000) {
                indexes.put(r, "test");
            } else {
                indexes.put(r, "train");
            }
        });
        return indexes;
    }

    private JSONObject loadConfig(Path toConfig) {
        JSONObject obj = null;
        try {
            obj = new JSONObject(new JSONTokener(Files.newInputStream(toConfig)));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return obj;
    }

    private void sinkLine(String line) {
        try {
            String[] lineParts = line.split("\t");
            boolean back = clang8Config.getBoolean(CLang8Enum.BACK.name());
            String whereTo = randomIndexes.get(counter.getAndAdd(1));
            switch (whereTo) {
                case "train":
                    if (!back){
                        srcTrainWriter.append(lineParts[0].trim()).append("\n");
                        trgTrainWriter.append(lineParts[1].trim()).append("\n");
                    } else {
                        srcTrainWriter.append(lineParts[1].trim()).append("\n");
                        trgTrainWriter.append(lineParts[0].trim()).append("\n");
                    }
                    break;
                case "valid":
                    if (!back){
                        srcValidWriter.append(lineParts[0].trim()).append("\n");
                        trgValidWriter.append(lineParts[1].trim()).append("\n");
                    } else {
                        srcValidWriter.append(lineParts[1].trim()).append("\n");
                        trgValidWriter.append(lineParts[0].trim()).append("\n");
                    }
                    break;
                case "test":
                    if (!back){
                        srcTestWriter.append(lineParts[0].trim()).append("\n");
                        trgTestWriter.append(lineParts[1].trim()).append("\n");
                    } else {
                        srcTestWriter.append(lineParts[1].trim()).append("\n");
                        trgTestWriter.append(lineParts[0].trim()).append("\n");
                    }
                    break;
                default:
                    ToConsole.printLine(String.format("PROBLEM: $", line), LEVEL+1);
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public boolean readAndSink(int subLevel) {
        String dataDir = (String) clang8Config.get(CLang8Enum.DATA_DIR.name());

        // Get the number of instances of clang8
        ShellExecutor.setDirectory(dataDir.concat("/downloaded"));
        Integer cLang8Size = Integer.parseInt(ShellExecutor.exec(String.format("wc -l %s | awk '{print $1}'", (String) clang8Config.get(CLang8Enum.C_LANG_8.name()))).replace("\n", ""));

        // Generate random int list from 0 to cLang8Size
        randomIndexes = getIndexes(cLang8Size);
        counter = new AtomicInteger(0);
        try {
            // Reader for cLangFile
            BufferedReader reader = Files.newBufferedReader(Paths.get(dataDir.concat("/downloaded"), (String) clang8Config.get(CLang8Enum.C_LANG_8.name())));

            final UnicodePunctuationNormalizer normalizer = new UnicodePunctuationNormalizer();
            //Writers for each one of the six resulting files
            srcTrainWriter = Files.newBufferedWriter(Paths.get(dataDir, (String) clang8Config.get(CLang8Enum.C_LANG_8_SRC_TRAIN.name())));
            trgTrainWriter = Files.newBufferedWriter(Paths.get(dataDir, (String) clang8Config.get(CLang8Enum.C_LANG_8_TRG_TRAIN.name())));
            srcValidWriter = Files.newBufferedWriter(Paths.get(dataDir, (String) clang8Config.get(CLang8Enum.C_LANG_8_SRC_VALID.name())));
            trgValidWriter = Files.newBufferedWriter(Paths.get(dataDir, (String) clang8Config.get(CLang8Enum.C_LANG_8_TRG_VALID.name())));
            srcTestWriter = Files.newBufferedWriter(Paths.get(dataDir, (String) clang8Config.get(CLang8Enum.C_LANG_8_SRC_TEST.name())));
            trgTestWriter = Files.newBufferedWriter(Paths.get(dataDir, (String) clang8Config.get(CLang8Enum.C_LANG_8_TRG_TEST.name())));

            // Process and sink the lines
            AtomicInteger counter = new AtomicInteger(0);
            reader.lines().forEach(line -> {
                ToConsole.print(String.format("Number of lines processed this far: [%,d]", counter.addAndGet(1)), subLevel);
                sinkLine(normalizer.normalize(line));
            });
            ToConsole.printLine("", 0);

            reader.close();
            srcTrainWriter.close();
            trgTrainWriter.close();
            srcValidWriter.close();
            trgValidWriter.close();
            srcTestWriter.close();
            trgTestWriter.close();

            assertFileSizes();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void process(final int subLevel){
        LocalDateTime start = LocalDateTime.now();
        ToConsole.printLine("Reading and sinking...", subLevel);
        this.readAndSink(subLevel+1);
        Duration duration = Duration.between(start, LocalDateTime.now());
        ToConsole.printLine(String.format("Duration: %02d:%02d:%02d", duration.toSeconds() / 3600, (duration.toSeconds() % 3600) / 60, (duration.toSeconds() % 60)), subLevel);
        ToConsole.printLine("The End!", subLevel);
    }

    public static void main(String[] args) {
        CLang8Reader reader = new CLang8Reader("/home/baosiek/Development/Java/grammar/src/main/resources/");
        reader.readAndSink(1);
        System.out.println("The End!");
    }
}
