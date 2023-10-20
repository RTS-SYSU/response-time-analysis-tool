package com.demo.tool;

import com.demo.tool.responsetimeanalysis.analysis.SchedulabilityForMCS;
import com.demo.tool.responsetimeanalysis.entity.Resource;
import com.demo.tool.responsetimeanalysis.entity.SporadicTask;
import com.demo.tool.responsetimeanalysis.generator.AllocationGeneator;
import com.demo.tool.responsetimeanalysis.generator.PriorityGenerator;
import com.demo.tool.responsetimeanalysis.generator.SystemGenerator;
import com.demo.tool.responsetimeanalysis.utils.Factors;
import com.demo.tool.responsetimeanalysis.utils.Pair;

import java.util.ArrayList;

public class Analysis {
    AllocationGeneator allocGenerator = new AllocationGeneator();

    public Pair<ArrayList<ArrayList<SporadicTask>>, ArrayList<Resource>> generateSystem(Factors factors) {
        //系统任务生成
        SystemGenerator generator = new SystemGenerator(factors.MIN_PERIOD, factors.MAX_PERIOD, true,
                factors.TOTAL_PARTITIONS, factors.NUMBER_OF_TASKS,
                factors.RESOURCE_SHARING_FACTOR, factors.CL_RANGE_LOW, factors.CL_RANGE_HIGH,
                factors.TOTAL_RESOURCES, factors.NUMBER_OF_MAX_ACCESS_TO_ONE_RESOURCE, factors.UTILISATION, false);

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
        ArrayList<ArrayList<SporadicTask>> tasks = null;
        switch (factors.ALLOCATION) {
            case "WF" -> tasks = allocGenerator.allocateTasks(tasksToAlloc, resources, factors.TOTAL_PARTITIONS, 0);
            case "BF" -> tasks = allocGenerator.allocateTasks(tasksToAlloc, resources, factors.TOTAL_PARTITIONS, 1);
            case "FF" -> tasks = allocGenerator.allocateTasks(tasksToAlloc, resources, factors.TOTAL_PARTITIONS, 2);
            case "NF" -> tasks = allocGenerator.allocateTasks(tasksToAlloc, resources, factors.TOTAL_PARTITIONS, 3);

        }
        switch (factors.PRIORITY) {
            case "DMPO" -> new PriorityGenerator().assignPrioritiesByDM(tasks);
        }
        return new Pair<>(tasks, resources);
    }

    public boolean chooseSystemMode(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, Factors factors) {
        SchedulabilityForMCS mcs = new SchedulabilityForMCS();
        mcs.tasksRefresh(tasks);
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
        factors.schedulable = chooseSystemMode(tasks, resources, factors);
//        System.out.println("done");
    }
}
