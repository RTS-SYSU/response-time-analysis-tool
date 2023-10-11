package com.demo.tool.responsetimeanalysis.generator;

import java.util.ArrayList;
import java.util.Random;

/*A. Burns and R. I. Davis. Improved priority assignment for global Fixed priority 
	pre-emptive scheduling in multiprocessor real-time systems. Real-Time Systems, 47(1):1{40, 2010.
 * */

public class UUnifastDiscard {

	private double uUtil;
	private int uNum;
	private ArrayList<Double> uUs;
	private boolean shallDiscard;
	private int discardNum;

	public UUnifastDiscard(double util, int num, int discard) {
		this.uUtil = util;
		this.uNum = num;
		this.uUs = new ArrayList<Double>();
		this.shallDiscard = false;
		this.discardNum = discard;
	}

	public void setUtil(double x) {
		this.uUtil = x;
	}

	public void setNum(int x) {
		this.uNum = x;
	}

	public double getUtil() {
		return this.uUtil;
	}

	public int getNum() {
		return this.uNum;
	}

	public ArrayList<Double> getUtils() {
		if (uUifastDiscard())
			return uUs;
		else
			return null;
	}

	private boolean uUnifast() {
		uUs.clear();
		double sumU = this.uUtil;
		double nextSum = 0;
		double temp = 0;
		this.shallDiscard = false;
		Random r = new Random();
		for (int i = 1; i < this.uNum; i++) {

			nextSum = sumU * Math.pow(r.nextDouble(), (1.0 / (this.uNum - i)));
			temp = sumU - nextSum;
			if (temp > 1) {
				this.shallDiscard = true;
				break;
			}
			this.uUs.add(temp);
			sumU = nextSum;
		}
		if (!shallDiscard) {
			if (sumU < 1)
				uUs.add(sumU);
			else
				shallDiscard = true;
		}
		return this.shallDiscard;

	}

	private boolean uUifastDiscard() {
		boolean isComplete = false;
		for (int i = 0; i < this.discardNum; i++) {
			if (!this.uUnifast()) {
				isComplete = true;
				break;
			} else {
				this.uUs.clear();
			}
		}
		return isComplete;
	}

}