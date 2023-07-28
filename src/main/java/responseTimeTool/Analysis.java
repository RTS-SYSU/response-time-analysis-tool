package responseTimeTool;

import responseTimeTool.analysis.*;
import responseTimeTool.entity.Resource;
import responseTimeTool.entity.SporadicTask;
import responseTimeTool.generatorTools.AllocationGeneator;
import responseTimeTool.generatorTools.SystemGenerator;
import responseTimeTool.utils.AnalysisUtils;
import responseTimeTool.utils.Factors;
import responseTimeTool.utils.Pair;

import java.util.ArrayList;

public class Analysis {

    AllocationGeneator allocGenerator = new AllocationGeneator();

    public Pair<ArrayList<SporadicTask>, ArrayList<Resource>> generateSystem(Factors factors) {
        //系统任务生成
        SystemGenerator generator = new SystemGenerator(factors.MIN_PERIOD, factors.MAX_PERIOD, true,
                factors.TOTAL_PARTITIONS, factors.NUMBER_OF_TASKS,
                factors.RESOURCE_SHARING_FACTOR, factors.CL_RANGE_LOW, factors.CL_RANGE_HIGH,
                AnalysisUtils.RESOURCES_RANGE.PARTITIONS, factors.NUMBER_OF_MAX_ACCESS_TO_ONE_RESOURCE, false);

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
        return new Pair(tasksToAlloc, resources);
    }

    public boolean chooseSystemMode(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, Factors factors) {
        SchedulabilityForMCS mcs = new SchedulabilityForMCS();
        switch (factors.SYSTEM_MODE) {
            case "LO":
                if (mcs.isSchedulableForLowMode(tasks, resources, false)) return true;
            case "HI":
                if (mcs.isSchedulableForHighMode(tasks, resources, false)) return true;
            case "ModeSwitch":
                if (mcs.isSchedulableForModeSwitch(tasks, resources, false)) return true;
        }
        return false;
    }

    public boolean analysis(Factors factors, ArrayList<SporadicTask> tasksToAlloc, ArrayList<Resource> resources) {
        //number of schedulable system for whole mode
        switch (factors.ALLOCATION) {
            case "WF" -> {
                ArrayList<ArrayList<SporadicTask>> tasksWF = allocGenerator.allocateTasks(tasksToAlloc, resources, factors.TOTAL_PARTITIONS, 0);
                return chooseSystemMode(tasksWF, resources, factors);
            }
            case "BF" -> {
                ArrayList<ArrayList<SporadicTask>> tasksBF = allocGenerator.allocateTasks(tasksToAlloc, resources, factors.TOTAL_PARTITIONS, 1);
                return chooseSystemMode(tasksBF, resources, factors);
            }
            case "FF" -> {
                ArrayList<ArrayList<SporadicTask>> tasksFF = allocGenerator.allocateTasks(tasksToAlloc, resources, factors.TOTAL_PARTITIONS, 2);
                return chooseSystemMode(tasksFF, resources, factors);
            }
            case "NF" -> {
                ArrayList<ArrayList<SporadicTask>> tasksNF = allocGenerator.allocateTasks(tasksToAlloc, resources, factors.TOTAL_PARTITIONS, 3);
                return chooseSystemMode(tasksNF, resources, factors);
            }
        }
        return false;
//        System.out.println("done");
    }
}
