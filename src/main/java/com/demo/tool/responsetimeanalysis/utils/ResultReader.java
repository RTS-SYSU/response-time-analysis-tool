package com.demo.tool.responsetimeanalysis.utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class ResultReader {

	public static void main(String[] args) {

		schedreader();
		migReader();
		priorityReader();
	}

	public static void schedreader() {
		String result = "Work Load \n";
		for (int bigSet = 1; bigSet < 10; bigSet++) {

			for (int smallSet = 1; smallSet < 200; smallSet++) {
				String filepath = "result/" + "1" + " " + bigSet + " " + smallSet + ".txt";

				List<String> lines = null;
				try {
					lines = Files.readAllLines(Paths.get(filepath), StandardCharsets.UTF_8);
				} catch (IOException e) {
				}
				if (lines != null)
					result += bigSet + "" + smallSet + " " + lines.get(0) + "\n";
			}

			result += "\n";

		}
		result += "\n \n CS Length \n";

		for (int bigSet = 1; bigSet < 10; bigSet++) {

			for (int smallSet = 1; smallSet < 10; smallSet++) {
				String filepath = "result/" + "2" + " " + bigSet + " " + smallSet + ".txt";

				List<String> lines = null;
				try {
					lines = Files.readAllLines(Paths.get(filepath), StandardCharsets.UTF_8);
				} catch (IOException e) {
				}
				if (lines != null)
					result += bigSet + "" + smallSet + " " + lines.get(0) + "\n";
			}

			result += "\n";

		}
		result += "\n \n Resource Access \n";

		for (int bigSet = 1; bigSet < 10; bigSet++) {

			for (int smallSet = 1; smallSet < 42; smallSet++) {
				String filepath = "result/" + "3" + " " + bigSet + " " + smallSet + ".txt";

				List<String> lines = null;
				try {
					lines = Files.readAllLines(Paths.get(filepath), StandardCharsets.UTF_8);
				} catch (IOException e) {
				}

				if (lines != null)
					result += bigSet + "" + smallSet + " " + lines.get(0) + "\n";
			}

			result += "\n";

		}
		result += "\n \n Parallelism \n";

		for (int bigSet = 1; bigSet < 10; bigSet++) {

			for (int smallSet = 1; smallSet < 42; smallSet++) {
				String filepath = "result/" + "4" + " " + bigSet + " " + smallSet + ".txt";

				List<String> lines = null;
				try {
					lines = Files.readAllLines(Paths.get(filepath), StandardCharsets.UTF_8);
				} catch (IOException e) {
				}

				if (lines != null)
					result += bigSet + "" + smallSet + " " + lines.get(0) + "\n";
			}

			result += "\n";

		}

		System.out.println(result);

		PrintWriter writer = null;
		try {
			writer = new PrintWriter(new FileWriter(new File("result/all.txt"), false));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		writer.println(result);
		writer.close();
	}

	public static void migReader() {
		String result = "Work Load \n";
		for (int bigSet = 1; bigSet < 6; bigSet++) {

			// result += "access: " + (2 + (bigSet - 1) * 3) + " and rsf: " +
			// (.2 + (bigSet - 1) * .3) + "\n";

			for (int smallSet = 1; smallSet < 20; smallSet++) {
				String filepath = "result/" + "mig 1" + " " + bigSet + " " + smallSet + ".txt";

				List<String> lines = null;
				try {
					lines = Files.readAllLines(Paths.get(filepath), StandardCharsets.UTF_8);
				} catch (IOException e) {
				}
				if (lines != null)
					result += bigSet + "" + smallSet + " " + lines.get(0) + "\n";
			}

			result += "\n";

		}
		result += "\n \n CS Length \n";

		for (int bigSet = 1; bigSet < 4; bigSet++) {
			result += "tasks per core: " + (3 + (bigSet - 1) * 2) + "\n";

			for (int smallSet = 1; smallSet < 301; smallSet++) {
				String filepath = "result/" + "mig 2" + " " + bigSet + " " + smallSet + ".txt";

				List<String> lines = null;
				try {
					lines = Files.readAllLines(Paths.get(filepath), StandardCharsets.UTF_8);
				} catch (IOException e) {
				}
				if (lines != null)
					result += bigSet + "" + smallSet + " " + lines.get(0) + "\n";
			}

			result += "\n";

		}
		result += "\n \n Resource Access \n";

		for (int bigSet = 1; bigSet < 4; bigSet++) {
			result += "tasks per core: " + (3 + (bigSet - 1) * 2) + "\n";

			for (int smallSet = 1; smallSet < 11; smallSet++) {
				String filepath = "result/" + "mig 3" + " " + bigSet + " " + smallSet + ".txt";

				List<String> lines = null;
				try {
					lines = Files.readAllLines(Paths.get(filepath), StandardCharsets.UTF_8);
				} catch (IOException e) {
				}

				if (lines != null)
					result += bigSet + "" + smallSet + " " + lines.get(0) + "\n";
			}

			result += "\n";

		}

		result += "\n \n Parallel \n";

		for (int partitions = 0; partitions < 50; partitions++) {
			// result += "tasks per core: " + (4 + 2 * partitions) + "\n";

			String filepath = "result/" + "mig 4" + " " + 1 + " " + (partitions) + ".txt";

			List<String> lines = null;
			try {
				lines = Files.readAllLines(Paths.get(filepath), StandardCharsets.UTF_8);
			} catch (IOException e) {
			}

			if (lines != null)
				result += 1 + "" + (partitions) + " " + lines.get(0) + "\n";

			// result += "\n";

		}

		System.out.println(result);

		PrintWriter writer = null;
		try {
			writer = new PrintWriter(new FileWriter(new File("result/all.txt"), false));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		writer.println(result);
		writer.close();
	}

	public static void priorityReader() {
		String result = "\n \n MSRP \n";

		for (int bigSet = 1; bigSet < 10; bigSet++) {

			for (int smallSet = 1; smallSet < 10; smallSet++) {
				String filepath = "result/" + "MSRP 2" + " " + bigSet + " " + smallSet + ".txt";

				List<String> lines = null;
				try {
					lines = Files.readAllLines(Paths.get(filepath), StandardCharsets.UTF_8);
				} catch (IOException e) {
				}
				if (lines != null)
					result += bigSet + "" + smallSet + " " + lines.get(0) + "\n";
			}

			result += "\n";

		}

		result += "\n \n PWLP \n";

		for (int bigSet = 1; bigSet < 10; bigSet++) {

			for (int smallSet = 1; smallSet < 10; smallSet++) {
				String filepath = "result/" + "PWLP 2" + " " + bigSet + " " + smallSet + ".txt";

				List<String> lines = null;
				try {
					lines = Files.readAllLines(Paths.get(filepath), StandardCharsets.UTF_8);
				} catch (IOException e) {
				}
				if (lines != null)
					result += bigSet + "" + smallSet + " " + lines.get(0) + "\n";
			}

			result += "\n";

		}

		result += "\n \n MrsP \n";

		for (int bigSet = 1; bigSet < 10; bigSet++) {

			for (int smallSet = 1; smallSet < 10; smallSet++) {
				String filepath = "result/" + "MrsP 2" + " " + bigSet + " " + smallSet + ".txt";

				List<String> lines = null;
				try {
					lines = Files.readAllLines(Paths.get(filepath), StandardCharsets.UTF_8);
				} catch (IOException e) {
				}
				if (lines != null)
					result += bigSet + "" + smallSet + " " + lines.get(0) + "\n";
			}

			result += "\n";

		}

		System.out.println(result);

		PrintWriter writer = null;
		try {
			writer = new PrintWriter(new FileWriter(new File("result/all.txt"), false));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		writer.println(result);
		writer.close();
	}
}
