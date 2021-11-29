package ca.baosiek;

import ca.baosiek.utils.ToConsole;
import org.apache.commons.cli.*;

public class GrammarManager {

    private static final int LEVEL = 1;

    public static void main(String[] args) {

        Options options = new Options();
        Option builderOption = new Option("b", "builder", true,
                "builder type: bb (for BatchBuilder), cb (for CorpusBuilder), cbe (for CorpusBuilderModelEnlarged), " +
                            "owt (for OpenWEbTextReader), cl8 (for CLang8Reader)");
        builderOption.setRequired(true);
        options.addOption(builderOption);

        Option homeDirOption = new Option("h", "home", true, "Home directory of the application");
        homeDirOption.setRequired(true);
        options.addOption(homeDirOption);

        Option quantityOption = new Option("q", "quantity", true, "if builder type is bb, quantity must be assigned");
        options.addOption(quantityOption);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null;//not a good practice, it serves it purpose

        try {
            cmd = parser.parse(options, args);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            formatter.printHelp("utility-name", options);
            System.exit(1);
        }

        String homeDir = cmd.getOptionValue("home");
        String builder = cmd.getOptionValue("builder");
        Integer quantity = 0;
        if (builder.equals("bb")){
            if (cmd.getOptionValue("quantity") != null){
                quantity = Integer.parseInt(cmd.getOptionValue("quantity"));
            } else {
                formatter.printHelp("utility-name", options);
                System.exit(1);
            }
        }

        ToConsole.printLine(String.format("Home directory set to: %s", homeDir), LEVEL);
        ToConsole.printLine(String.format("Manager set to run : %s", builder), LEVEL);
        if (builder.equals("bb")){
            System.out.printf("batch size set to: %d\n", quantity);
        }

        switch (builder) {
            case "bb":
                ToConsole.printLine("Starting BatchBuilder...", LEVEL);
                BatchBuilder batchBuilder = new BatchBuilder(homeDir);
                batchBuilder.process(quantity);
                break;
            case "cbe":
                ToConsole.printLine("Starting CorpusBuilderModelEnlarged...", LEVEL);
                CorpusBuilderModelEnlarged cbe = new CorpusBuilderModelEnlarged(homeDir);
                cbe.process();
                break;
            case "cb":
                ToConsole.printLine("Starting CorpusBuilder...", LEVEL);
                CorpusBuilder cb = new CorpusBuilder(homeDir);
                cb.process(LEVEL+1);
                ToConsole.printLine("CorpusBuilder Ended!", LEVEL);
                break;
            case "owt":
                ToConsole.printLine("Starting OpenWebTextReader...", LEVEL);
                OpenWebTextReader owt = new OpenWebTextReader(homeDir);
                owt.process(LEVEL+1);
                ToConsole.printLine("OpenWebTextReader Ended!", LEVEL);
                break;
            case "cl8":
                ToConsole.printLine("Starting CLang8Reader...", LEVEL);
                CLang8Reader cl8 = new CLang8Reader(homeDir);
                cl8.process(LEVEL+1);
                ToConsole.printLine("CLang8Reader Ended!", LEVEL);
                break;
            default:
                ToConsole.printLine("No existing builder was selected.", LEVEL);
                break;
        }
    }
}
