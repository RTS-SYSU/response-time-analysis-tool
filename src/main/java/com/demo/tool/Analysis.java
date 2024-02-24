package com.demo.tool;

import com.demo.tool.responsetimeanalysis.analysis.SchedulabilityForMCS;
import com.demo.tool.responsetimeanalysis.entity.Resource;
import com.demo.tool.responsetimeanalysis.entity.SporadicTask;
import com.demo.tool.responsetimeanalysis.generator.AllocationGeneator;
import com.demo.tool.responsetimeanalysis.generator.PriorityGenerator;
import com.demo.tool.responsetimeanalysis.generator.SystemGenerator;
import com.demo.tool.responsetimeanalysis.utils.Factors;
import com.demo.tool.responsetimeanalysis.utils.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

public class Analysis {
    public static Logger log = LogManager.getLogger();
    AllocationGeneator allocGenerator = new AllocationGeneator();

    public Pair<ArrayList<ArrayList<SporadicTask>>, ArrayList<Resource>> generateSystem(Factors factors) {

        log.info("System generation started");
        //系统任务生成
        SystemGenerator generator = new SystemGenerator(factors.MIN_PERIOD, factors.MAX_PERIOD, true, factors.TOTAL_PARTITIONS, factors.NUMBER_OF_TASKS, factors.RESOURCE_SHARING_FACTOR, factors.CL_RANGE_LOW, factors.CL_RANGE_HIGH, factors.TOTAL_RESOURCES, factors.NUMBER_OF_MAX_ACCESS_TO_ONE_RESOURCE, factors.UTILISATION, false);

        log.info("Number of cores: " + factors.TOTAL_PARTITIONS);
        log.info("Number of tasks: " + factors.NUMBER_OF_TASKS);
        log.info("Utilization rate: " + factors.UTILISATION);
        log.info("Period range: " + factors.MIN_PERIOD + " - " + factors.MAX_PERIOD);
        log.info("Number of resources: " + factors.TOTAL_RESOURCES);
        log.info("Resource sharing factor: " + factors.RESOURCE_SHARING_FACTOR);
        log.info("Number of max access to one resource: " + factors.NUMBER_OF_MAX_ACCESS_TO_ONE_RESOURCE);
        log.info("Critical section range: " + factors.CL_RANGE_LOW + " - " + factors.CL_RANGE_HIGH);
        log.info("Allocation method: " + factors.ALLOCATION);
        log.info("Priority ordering method: " + factors.PRIORITY);


        ArrayList<SporadicTask> tasksToAlloc = null;
        ArrayList<Resource> resources = null;
        while (tasksToAlloc == null) {
            tasksToAlloc = generator.generateTasks(true);
            resources = generator.generateResources();

            generator.generateResourceUsage(tasksToAlloc, resources);

            int allocOK = 0;

            //保证生成的系统任务可成功分配到某个核心
            for (int a = 0; a < 6; a++)
                if (allocGenerator.allocateTasks(tasksToAlloc, resources, factors.TOTAL_PARTITIONS, a) != null)
                    allocOK++;

            if (allocOK != 6) tasksToAlloc = null;

        }
        log.info("tasks generated");
        log.info("resources generated");
        ArrayList<ArrayList<SporadicTask>> tasks = null;

        log.info(factors.ALLOCATION + " allocation selected");

        switch (factors.ALLOCATION) {
            case "WF" -> tasks = allocGenerator.allocateTasks(tasksToAlloc, resources, factors.TOTAL_PARTITIONS, 0);
            case "BF" -> tasks = allocGenerator.allocateTasks(tasksToAlloc, resources, factors.TOTAL_PARTITIONS, 1);
            case "FF" -> tasks = allocGenerator.allocateTasks(tasksToAlloc, resources, factors.TOTAL_PARTITIONS, 2);
            case "NF" -> tasks = allocGenerator.allocateTasks(tasksToAlloc, resources, factors.TOTAL_PARTITIONS, 3);

        }
        log.info("Task assignment completed");

        switch (factors.PRIORITY) {
            case "DMPO" -> new PriorityGenerator().assignPrioritiesByDM(tasks);
        }
        log.info(factors.PRIORITY + " priority method selected");

        log.info("System generation completed");

        return new Pair<>(tasks, resources);
    }

    public boolean chooseSystemMode(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, Factors factors) {
        SchedulabilityForMCS mcs = new SchedulabilityForMCS();
        mcs.tasksRefresh(tasks);
        log.info("System Mode:" + factors.SYSTEM_MODE);
        log.info("Analysis Mode: " + factors.ANALYSIS_MODE);
        switch (factors.SYSTEM_MODE) {
            case "LO" -> {
                if (mcs.isSchedulableForLowMode(factors.ANALYSIS_MODE, tasks, resources)) return true;
            }
            case "HI" -> {
                if (mcs.isSchedulableForHighMode(factors.ANALYSIS_MODE, tasks, resources)) return true;
            }
            case "ModeSwitch" -> {
                if (mcs.isSchedulableForModeSwitch(factors.ANALYSIS_MODE, tasks, resources)) return true;
            }
        }
        return false;
    }

    public void analysis(Factors factors, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources) {
        log.info("Analysis started");
        factors.schedulable = chooseSystemMode(tasks, resources, factors);
        log.info("Analysis completed");
//        System.out.println("done");
    }

    public int[][] batchAnalysis(Factors factors, int sysNums) {
        log.info("Batch Test started");

        var res = new int[3][3];

        for (int i = 0; i < sysNums; i++) {

            var pair = generateSystem(factors);
            var tasks = pair.getFirst();
            var resources = pair.getSecond();

            SchedulabilityForMCS mcs = new SchedulabilityForMCS();
            mcs.tasksRefresh(tasks);

            if (mcs.isSchedulableForLowMode("MSRP", tasks, resources)) {
                res[0][0]++;
            }
            if (mcs.isSchedulableForHighMode("MSRP", tasks, resources)) {
                res[0][1]++;
            }
            if (mcs.isSchedulableForModeSwitch("MSRP", tasks, resources)) {
                res[0][2]++;
            }

            if (mcs.isSchedulableForLowMode("Mrsp", tasks, resources)) {
                res[1][0]++;
            }
            if (mcs.isSchedulableForHighMode("Mrsp", tasks, resources)) {
                res[1][1]++;
            }
            if (mcs.isSchedulableForModeSwitch("Mrsp", tasks, resources)) {
                res[1][2]++;
            }

            if (mcs.isSchedulableForLowMode("PWLP", tasks, resources)) {
                res[2][0]++;
            }
            if (mcs.isSchedulableForHighMode("PWLP", tasks, resources)) {
                res[2][1]++;
            }
            if (mcs.isSchedulableForModeSwitch("PWLP", tasks, resources)) {
                res[2][2]++;
            }
        }
        log.info("Batch Test completed");
        return res;
    }
}
