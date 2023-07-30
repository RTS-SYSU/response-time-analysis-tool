package responseTimeTool;

import responseTimeTool.analysis.SchedulabilityForMCS;
import responseTimeTool.entity.Resource;
import responseTimeTool.entity.SporadicTask;
import responseTimeTool.generatorTools.AllocationGeneator;
import responseTimeTool.generatorTools.PriorityGenerator;
import responseTimeTool.generatorTools.SystemGenerator;
import responseTimeTool.utils.AnalysisUtils;
import responseTimeTool.utils.Factors;
import responseTimeTool.utils.Pair;

import java.util.ArrayList;

public class Analysis {

    AllocationGeneator allocGenerator = new AllocationGeneator();

    public Pair<ArrayList<ArrayList<SporadicTask>>, ArrayList<Resource>> generateSystem(Factors factors) {
        //系统任务生成
        SystemGenerator generator = new SystemGenerator(factors.MIN_PERIOD, factors.MAX_PERIOD, true,
                factors.TOTAL_PARTITIONS, factors.NUMBER_OF_TASKS,
                factors.RESOURCE_SHARING_FACTOR, factors.CL_RANGE_LOW, factors.CL_RANGE_HIGH,
                AnalysisUtils.RESOURCES_RANGE.PARTITIONS, factors.NUMBER_OF_MAX_ACCESS_TO_ONE_RESOURCE, factors.UTILISATION, false);

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
            case "WF" -> {
                tasks = allocGenerator.allocateTasks(tasksToAlloc, resources, factors.TOTAL_PARTITIONS, 0);
            }
            case "BF" -> {
                tasks = allocGenerator.allocateTasks(tasksToAlloc, resources, factors.TOTAL_PARTITIONS, 1);
            }
            case "FF" -> {
                tasks = allocGenerator.allocateTasks(tasksToAlloc, resources, factors.TOTAL_PARTITIONS, 2);
            }
            case "NF" -> {
                tasks = allocGenerator.allocateTasks(tasksToAlloc, resources, factors.TOTAL_PARTITIONS, 3);
            }
        }
        new PriorityGenerator().assignPrioritiesByDM(tasks);
        return new Pair(tasks, resources);
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

    public void analysis(Factors factors, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources) {
        factors.schedulable = chooseSystemMode(tasks, resources, factors);
//        System.out.println("done");
    }
}