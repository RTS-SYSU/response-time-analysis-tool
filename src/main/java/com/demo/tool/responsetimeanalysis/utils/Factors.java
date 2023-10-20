package com.demo.tool.responsetimeanalysis.utils;

public class Factors {
    public int MAX_PERIOD = -1;//周期范围
    public int MIN_PERIOD = -1;
    public int TOTAL_PARTITIONS = -1;//分区数m（核心数）

    public int CL_RANGE_LOW = -1;    //资源临界区长度
    public int CL_RANGE_HIGH = -1;    //资源临界区长度
    public int NUMBER_OF_MAX_ACCESS_TO_ONE_RESOURCE = -1;   //一个任务可以访问一个资源的最大次数
    public int NUMBER_OF_TASKS = -1;

    public double UTILISATION = -1;
    public String ALLOCATION;
    public String SYSTEM_MODE;
    public String ANALYSIS_MODE;
    public String PRIORITY;
    public double RESOURCE_SHARING_FACTOR = -1;    //资源共享因子
    public int TOTAL_RESOURCES = -1;

    public boolean schedulable = false;

}
