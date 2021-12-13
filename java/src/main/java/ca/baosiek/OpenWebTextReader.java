package ca.baosiek;

import ca.baosiek.utils.OWTEnum;
import ca.baosiek.utils.ToConsole;
import ca.baosiek.utils.UnicodePunctuationNormalizer;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * TODO: Change enumeration to Interface
 */

public class OpenWebTextReader {

    private final JSONObject owtConfig;
    private final Pattern p;

    public OpenWebTextReader(String homeDir) {
        Path owtConfigPath = Path.of(homeDir, "config","owt.json");
        owtConfig = loadConfig(owtConfigPath);
        String TAG_PATTERN = "<([a-z]+ )";
        this.p = Pattern.compile(TAG_PATTERN);
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

    private List<String> filterNumberSequence(String l) {
        String pattern = "[0-9]{7}-[0-9a-z]{32}.txt ([0-9]{7} ){2,3}([0-9]{11} ){2}[0-9]{6} [0-9]? ustar ([0-9]{7} ?){2}";
        String noise = "\u0000+";
        l = l.replaceAll(noise, " ").replaceAll("\\s{2,}", " ").trim();
        l = l.replaceAll(pattern, "\t");
        return Arrays.asList(l.split("\t"));
    }

    private boolean filterLineByContent(String line){
        if (line == null) return false;
        if (line.length() < 3) return false;
        Matcher m = this.p.matcher(line);
        return !m.find();
    }

    private List<String> shuffleList(List<String> content){
        Collections.shuffle(content);
        return content;
    }

    public Optional<List<String>> getContent(String fileName, Integer size, Integer count, int subLevel) {
        ToConsole.print(String.format("Number of files processed this far: [%,d] of [%,d]", count, size), subLevel);
        List<String> content = new ArrayList<>();
        try {
            BufferedReader br = Files.newBufferedReader(Path.of((String) owtConfig.get(OWTEnum.DOWNLOADED_DIR.name()), fileName));
            content = br.lines().map(this::filterNumberSequence).flatMap(Collection::stream).filter(this::filterLineByContent).map(String::trim).collect(Collectors.toList());
            br.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return Optional.of(content);
    }

    public void readAndSink(final int subLevel) {
        String downLoaded = (String) owtConfig.get(OWTEnum.DOWNLOADED_DIR.name());
        UnicodePunctuationNormalizer normalizer = new UnicodePunctuationNormalizer();
        try {
            int bufferSize = 4096;
            BufferedWriter owtTrainWriter = new BufferedWriter(new FileWriter(Path.of((String) owtConfig.get(OWTEnum.DATA_DIR.name()), (String) owtConfig.get(OWTEnum.OWT_TRG_TRAIN.name())).toFile()), bufferSize);
            BufferedWriter owtValidWriter = new BufferedWriter(new FileWriter(Path.of((String) owtConfig.get(OWTEnum.DATA_DIR.name()), (String) owtConfig.get(OWTEnum.OWT_TRG_VALID.name())).toFile()), bufferSize);
            BufferedWriter owtTestWriter = new BufferedWriter(new FileWriter(Path.of((String) owtConfig.get(OWTEnum.DATA_DIR.name()), (String) owtConfig.get(OWTEnum.OWT_TRG_TEST.name())).toFile()), bufferSize);

            AtomicInteger fileCounter = new AtomicInteger(0);
            AtomicInteger lineCounter = new AtomicInteger(0);

            File downLoadedDir = Files.createDirectories(Path.of(downLoaded)).toFile();

            List<String> files = shuffleList(Arrays.asList(Objects.requireNonNull(downLoadedDir.list())));
            files.parallelStream().map(f -> getContent(f, files.size(), fileCounter.addAndGet(1), subLevel))
                    .filter(Optional::isPresent) // if getContent throws an exception, content can be null
                    .flatMap(content -> shuffleList(content.get()).stream().limit(880))
                    .map(normalizer::normalize)
                    .forEach(line -> {
                        try {
                            /*
                             * 0.1% of the lines go to valid and test, remaining 99,8% to train. Available GPU
                             * does not have memory for more.
                             */
                            int mod = lineCounter.addAndGet(1) % 1000;
                            switch(mod){
                                case 0:
                                    owtTestWriter.append(line).append("\n");
                                    break;
                                case 1:
                                    owtValidWriter.append(line).append("\n");
                                    break;
                                default:
                                    owtTrainWriter.append(line).append("\n");
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
            ToConsole.lineFeed();

            owtTrainWriter.close();
            owtValidWriter.close();
            owtTestWriter.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void process(int subLevel){
        LocalDateTime start = LocalDateTime.now();
        ToConsole.printLine("Reading and sinking...", subLevel);
        this.readAndSink(subLevel+1);
        Duration duration = Duration.between(start, LocalDateTime.now());
        ToConsole.printLine(String.format("Duration: %02d:%02d:%02d", duration.toSeconds() / 3600, (duration.toSeconds() % 3600) / 60, (duration.toSeconds() % 60)), subLevel);
    }

    public static void main(String[] args) {
        LocalDateTime start = LocalDateTime.now();

        OpenWebTextReader reader = new OpenWebTextReader(args[0]);
        reader.readAndSink(0);

        Duration duration = Duration.between(start, LocalDateTime.now());
        ToConsole.printLine(String.format("Duration: %02d:%02d:%02d", duration.toSeconds() / 3600, (duration.toSeconds() % 3600) / 60, (duration.toSeconds() % 60)), 0);
        ToConsole.printLine("The End!", 0);
    }
}