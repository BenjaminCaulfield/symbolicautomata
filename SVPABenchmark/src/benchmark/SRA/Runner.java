package benchmark.SRA;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import java.io.Reader;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import org.apache.commons.cli.*;
import java.io.File;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ExecutionException;

import java.math.BigDecimal;

import com.google.common.base.Stopwatch;
import java.util.concurrent.TimeUnit;

public class Runner {

    private static Integer numberOfRuns = 3;
    private static Integer roundingPrecision = 3;
    private static Integer timeout = 300;
    private static Experiments sra = new Experiments();
    private static Method[] methods = sra.getClass().getMethods();
    private static ArrayList<String> alreadyRan = new ArrayList<String>();
    private static CSVReader csvReader = null;
    private static CSVWriter csvWriter = null;
    private static String[] potentialErrorRecord = {"IDENTIFIER"};
    private static File file = new File("./Experiments.csv");
    private static ArrayList<String> testsToRun = new ArrayList<>();

    public static void main(String[] args) {
        while(true) {
            try {

                System.out.println("-------------------------------------------------------------------------------");
                System.out.println("SRA Experiment Runner");
                System.out.println("-------------------------------------------------------------------------------");

                Options options = new Options();

                Option input = new Option("f", "file", true, "CSV file. Default: ./Experiments.csv");
                input.setRequired(false);
                options.addOption(input);

                Option list = new Option("l", "list", false, "List all Benchmarks.");
                input.setRequired(false);
                options.addOption(list);

                Option runs = new Option("n", "numberOfRuns", true, "Specify the number of runs per experiment. Default: 3");
                runs.setArgs(1);
                input.setRequired(false);
                options.addOption(runs);

                Option timeoutOption = new Option("t", "timeout", true, "Specify the timeout in whole seconds. Default: 300");
                runs.setArgs(1);
                input.setRequired(false);
                options.addOption(timeoutOption);

                Option testsOption = new Option("e", "experiments", true, "Experiments to run. Default: All");
                testsOption.setArgs(Option.UNLIMITED_VALUES);
                input.setRequired(false);
                options.addOption(testsOption);

                CommandLineParser parser = new DefaultParser();
                HelpFormatter formatter = new HelpFormatter();
                CommandLine cmd;

                try {
                    cmd = parser.parse(options, args);
                    String filePath = cmd.getOptionValue("file");
                    String[] experiments = cmd.getOptionValues("experiments");
                    String runsParsed = cmd.getOptionValue("numberOfRuns");
                    String timeoutParsed = cmd.getOptionValue("timeout");

                    if (filePath != null)
                        file = new File(filePath);

                    if (runsParsed != null)
                        numberOfRuns = Integer.parseInt(runsParsed);

                    if (timeoutParsed != null)
                        timeout = Integer.parseInt(timeoutParsed);

                    if (cmd.hasOption("l")) {
                        System.out.println("Benchmarks available:");
                        System.out.println();
                        System.out.println("all");
                        for (Method method : methods) {
                            if (method.getAnnotation(ToRun.class) != null)
                                System.out.println(method.getName());
                        }
                        System.exit(0);
                    }

                    if (experiments != null)
                        Collections.addAll(testsToRun, experiments);
                    else
                        testsToRun.add("all");

                } catch (ParseException e) {
                    System.out.println(e.getMessage());
                    formatter.printHelp("SRA", options);
                    System.exit(1);
                }

                if (!file.isFile())
                    file.createNewFile();

                Reader reader = Files.newBufferedReader(file.toPath());
                csvReader = new CSVReader(reader);
                String[] buffer;
                while ((buffer = csvReader.readNext()) != null)
                    alreadyRan.add(buffer[0]);
                csvReader.close();

                for (Method method : methods) {
                    if (testsToRun.contains(method.getName()) || testsToRun.contains("all")) {
                        if (method.getAnnotation(ToRun.class) != null && !alreadyRan.contains(method.getName())) {
                            csvWriter = new CSVWriter(new FileWriter(file, true));
                            potentialErrorRecord[0] = method.getName();
                            ArrayList<BigDecimal> timings = new ArrayList<BigDecimal>();
                            ArrayList<BigDecimal> validTimings = new ArrayList<BigDecimal>();

                            System.out.println("-------------------------------------------------------------------------------");
                            System.out.println("Now running: " + method.getName());
                            Future<Object> future = null;

                            try {
                                for (Integer iterator = 0; iterator < numberOfRuns; iterator++) {
                                    Stopwatch timer = Stopwatch.createStarted();
                                    ExecutorService executor = Executors.newCachedThreadPool();
                                    Callable<Object> task = new Callable<Object>() {
                                        public Object call() throws Exception {
                                            return method.invoke(sra);
                                        }
                                    };
                                    future = executor.submit(task);
                                    Object result = future.get(timeout, TimeUnit.SECONDS);

                                    timer.stop();
                                    BigDecimal timerResult = BigDecimal.valueOf(timer.elapsed(TimeUnit.NANOSECONDS)).divide(new BigDecimal("1000000000"), roundingPrecision, RoundingMode.UP);
                                    System.out.println("[" + (iterator + 1) + "] Done in: " + timerResult + " s.");
                                    timings.add(timerResult);

                                    if (iterator != 0 && iterator != 1) {
                                        BigDecimal cutOff = standardDeviation(timings).multiply(BigDecimal.valueOf(3));
                                        BigDecimal lower = mean(timings).subtract(cutOff);
                                        BigDecimal upper = mean(timings).add(cutOff);
                                        if (timerResult.compareTo(lower) < 0 || timerResult.compareTo(upper) > 0) {
                                            System.out.println("Upper Limit: " + upper.toString());
                                            System.out.println("Lower Limit: " + lower.toString());
                                            System.out.println("[" + (iterator + 1) + "] Marked as anomaly. Repeating.");
                                            iterator--;
                                        } else {
                                            validTimings.add(timerResult);
                                        }
                                    } else {
                                        validTimings.add(timerResult);
                                    }

                                }

                                System.out.println("[AVERAGE] Done in: " + mean(validTimings) + " s.");
                                validTimings.add(mean(validTimings));

                                ArrayList<String> outRecord = new ArrayList<String>();
                                outRecord.add(method.getName());
                                for (BigDecimal timming : validTimings)
                                    outRecord.add(timming.toString());

                                csvWriter.writeNext(outRecord.stream().toArray(String[]::new));
                                try {
                                    csvWriter.close();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            } catch (TimeoutException ex) {
                                System.out.println("Timeout while computing. Skipping.");
                                csvWriter.writeNext(potentialErrorRecord);
                                try {
                                    csvWriter.close();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            } catch (InterruptedException e) {
                                System.out.println("INTERRUPT");
                                // handle the interrupts
                            } catch (ExecutionException e) {
                                if (e.getCause().getCause() instanceof StackOverflowError)
                                    System.out.println("Stack overflow while computing. Skipping.");
                                else if (e.getCause().getCause() instanceof OutOfMemoryError)
                                    System.out.println("Ran out of memory while computing. Skipping.");
                                csvWriter.writeNext(potentialErrorRecord);
                                try {
                                    csvWriter.close();
                                } catch (Exception ee) {
                                    ee.printStackTrace();
                                }
                            } finally {
                                future.cancel(true);
                            }

                        } else if (alreadyRan.contains(method.getName())) {
                            System.out.println("Already ran " + method.getName() + ". Skipping.");
                        }
                    }
                }
                System.exit(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static BigDecimal mean(ArrayList<BigDecimal> results) {
        BigDecimal length = BigDecimal.valueOf(results.size());
        BigDecimal total = new BigDecimal("0");
        for (BigDecimal result : results)
            total = total.add(result);
        return total.divide(length, 3, RoundingMode.UP);
    }

    public static BigDecimal sqrt(BigDecimal A, final int SCALE) {
        BigDecimal x0 = BigDecimal.ZERO;
        BigDecimal x1 = new BigDecimal(Math.sqrt(A.doubleValue()));
        while (!x0.equals(x1)) {
            x0 = x1;
            x1 = A.divide(x0, SCALE, BigDecimal.ROUND_HALF_UP);
            x1 = x1.add(x0);
            x1 = x1.divide(BigDecimal.valueOf(2), SCALE, BigDecimal.ROUND_HALF_UP);

        }
        return x1;
    }

    private static BigDecimal standardDeviation(ArrayList<BigDecimal> results) {
        BigDecimal mean = mean(results);
        BigDecimal sum = BigDecimal.ZERO;
        for (BigDecimal value : results)
            sum = sum.add(value.subtract(mean).pow(2, new MathContext(roundingPrecision + 1)));
        return sqrt(sum.divide(BigDecimal.valueOf(results.size() - 1), roundingPrecision, RoundingMode.HALF_UP), roundingPrecision);
    }
}

