package responseTimeTool.generatorTools;

import responseTimeTool.entity.Resource;
import responseTimeTool.entity.SporadicTask;

import java.util.ArrayList;
import java.util.Random;

public class SystemGenerator {
    public int cs_len_low;
    public int cs_len_high;
    long csl = -1;
    public boolean isLogUni;
    public int maxT;
    public int minT;

    public int number_of_max_access;
//    public AnalysisUtils.RESOURCES_RANGE range;
    public int range;
    public double rsf;

    public int total_tasks;
    public int total_partitions;
    public double totalUtil;
    boolean print;

    double CP = 0.5;    //Criticality Proportion
    double CF = 2;    //Criticality Factor


    double maxUtilPerCore = 1;

    AllocationGeneator allocation = new AllocationGeneator();

    public SystemGenerator(int minT, int maxT, boolean isPeriodLogUni, int total_partitions, int totalTasks, double rsf, int cs_len_low, int cs_len_high,
                           int numberOfResources, int number_of_max_access, double utilisation, boolean print) {
        /*周期范围*/
        this.minT = minT;
        this.maxT = maxT;
//        this.totalUtil = 0.05 * (double) totalTasks;//总系统利用率
        this.totalUtil = utilisation;//总系统利用率
        this.total_partitions = total_partitions;//分区数 M 核心数
        this.total_tasks = totalTasks;
        this.isLogUni = isPeriodLogUni;
        this.cs_len_low = cs_len_low;    //critical section length
        this.cs_len_high = cs_len_high;
        this.range = numberOfResources;    //资源个数
        this.rsf = rsf;        //请求资源的任务比例
        this.number_of_max_access = number_of_max_access;    //一个任务访问一个资源的最大次数
        this.print = print;    //是否打印信息

        if (totalUtil / total_partitions <= 0.5)
            maxUtilPerCore = 0.5;
        else if (totalUtil / total_partitions <= 0.6)
            maxUtilPerCore = 0.6;
        else if (totalUtil / total_partitions <= 0.65)
            maxUtilPerCore = 0.65;
        else
            maxUtilPerCore = 1;
    }

    /*
     * generate task sets for multiprocessor fully partitioned fixed-priority
     * system
     */
    public ArrayList<SporadicTask> generateTasks(boolean allocationProtect) {
        ArrayList<SporadicTask> tasks = null;
        while (tasks == null) {
            tasks = generateT();
            if (allocationProtect && tasks != null && allocation.allocateTasks(tasks, null, total_partitions, 0) == null)
                tasks = null;
        }

        return tasks;
    }

    /* generate period*/
    private long generate_P() {
        Random random = new Random();
        long period;
        if (!isLogUni) {
            period = (random.nextInt(maxT - minT) + minT) * 1000;
        } else {
            double a1 = Math.log(minT);
            double a2 = Math.log(maxT + 1);
            double scaled = random.nextDouble() * (a2 - a1);
            double shifted = scaled + a1;
            double exp = Math.exp(shifted);

            int result = (int) exp;
            result = Math.max(minT, result);
            result = Math.min(maxT, result);

            period = result * 1000;
        }
        return period;
    }

    private ArrayList<SporadicTask> generateT() {
        int task_id = 1;
        ArrayList<SporadicTask> tasks = new ArrayList<>(total_tasks);
        ArrayList<Long> periods = new ArrayList<>(total_tasks);
        Random random = new Random();

        /* generates random periods */
        while (true) {
            if (!isLogUni) {
                long period = (random.nextInt(maxT - minT) + minT) * 1000;
                if (!periods.contains(period))
                    periods.add(period);
            } else {
                double a1 = Math.log(minT);
                double a2 = Math.log(maxT + 1);
                double scaled = random.nextDouble() * (a2 - a1);
                double shifted = scaled + a1;
                double exp = Math.exp(shifted);

                int result = (int) exp;
                result = Math.max(minT, result);
                result = Math.min(maxT, result);

                long period = result * 1000;
                if (!periods.contains(period))
                    periods.add(period);
            }

            if (periods.size() >= total_tasks)
                break;
        }
        periods.sort((p1, p2) -> Double.compare(p1, p2));


        /* generate utils */
        UUnifastDiscard unifastDiscard = new UUnifastDiscard(totalUtil, total_tasks, 1000); // 输入系统任务总利用率 和任务个数。返回的是每个任务的利用率
        ArrayList<Double> utils = null;
        while (true) {
            /* 每个任务的使用率 */
            utils = unifastDiscard.getUtils();
            double tt = 0;
            for (int i = 0; i < utils.size(); i++) {
                tt += utils.get(i);
            }

            if (utils != null)
                if (utils.size() == total_tasks && tt <= totalUtil)
                    break;
        }

        if (print) {
            System.out.print("task utils: ");
            double tt = 0;
            for (int i = 0; i < utils.size(); i++) {
                tt += utils.get(i);
                System.out.print(tt + "   ");
            }
            System.out.println("\n total uitls: " + tt);
        }

        /*select HI*/   //设置任务比例cp
        ArrayList<Integer> id_list = new ArrayList<Integer>();
        int HI_num = (int) (total_tasks * CP);

        while (HI_num != 0) {
            int id = random.nextInt(utils.size());
            if (!id_list.contains(id)) {
                id_list.add(id);
                HI_num--;
            }
        }

        //设置因子     U_High = U_Low * cf * cp?
        /* generate sporadic tasks */
        for (int i = 0; i < utils.size(); i++) {
            long computation_time = (long) (periods.get(i) * utils.get(i));
            int criti = 0;
            if (id_list.contains(i))
                criti = 1;
            if (computation_time == 0) {
                return null;
            }
            if (criti == 1 && computation_time * CF >= periods.get(i)) {
                return null;
            }
            SporadicTask t = new SporadicTask(-1, periods.get(i), computation_time, -1, task_id, utils.get(i), criti, CF);
            task_id++;
            tasks.add(t);
        }
        tasks.sort((p1, p2) -> -Double.compare(p1.util_LOW, p2.util_LOW)); /* 按照利用率排序，可以认为就是任务释放时间？ */

        return tasks;
    }

    /*
     * Generate a set of resources.
     */
    public ArrayList<Resource> generateResources() {
        /* generate resources from partitions/2 to partitions*2 */
        Random ran = new Random();
        int number_of_resources = range;

        ArrayList<Resource> resources = new ArrayList<>(number_of_resources);

        for (int i = 0; i < number_of_resources; i++) {
            long cs_len = 0;
            if (csl == -1) {
                cs_len = ran.nextInt(cs_len_high - cs_len_low) + cs_len_low + 1;
            } else
                cs_len = csl;

            Resource resource = new Resource(i + 1, cs_len);
            resources.add(resource);
        }

        resources.sort((r2, r1) -> Long.compare(r1.csl, r2.csl));

        for (int i = 0; i < resources.size(); i++) {
            Resource res = resources.get(i);
            res.id = i + 1;
        }

        return resources;
    }

    public void generateResourceUsage(ArrayList<SporadicTask> tasks, ArrayList<Resource> resources) {
        while (tasks == null)
            tasks = generateTasks(true);

        int fails = 0;
        Random ran = new Random();
        long number_of_resource_requested_tasks = Math.round(rsf * tasks.size());

        /* Generate resource usage */
        for (long l = 0; l < number_of_resource_requested_tasks; l++) {
            if (fails > 1000) {
                tasks = generateTasks(true);
                while (tasks == null)
                    tasks = generateTasks(true);
                l = 0;
                fails++;
            }
            int task_index = ran.nextInt(tasks.size());
            while (true) {
                if (tasks.get(task_index).resource_required_index.size() == 0)
                    break;
                task_index = ran.nextInt(tasks.size());
            }
            SporadicTask task = tasks.get(task_index);

            /* Find the resources that we are going to access */
            int number_of_requested_resource = ran.nextInt(resources.size()) + 1;
            for (int j = 0; j < number_of_requested_resource; j++) {
                while (true) {
                    int resource_index = ran.nextInt(resources.size());
                    if (!task.resource_required_index.contains(resource_index)) {
                        task.resource_required_index.add(resource_index);
                        break;
                    }
                }
            }
            task.resource_required_index.sort((r1, r2) -> Integer.compare(r1, r2));

            long total_resource_execution_time = 0;
            for (int k = 0; k < task.resource_required_index.size(); k++) {
                int number_of_requests = ran.nextInt(number_of_max_access) + 1;
                task.number_of_access_in_one_release.add(number_of_requests);
                total_resource_execution_time += number_of_requests * resources.get(task.resource_required_index.get(k)).csl;
            }

            if (total_resource_execution_time > task.C_LOW) {
                l--;
                task.resource_required_index.clear();
                task.number_of_access_in_one_release.clear();
                fails++;
            } else {
                task.C_LOW = task.C_LOW - total_resource_execution_time;
                if (task.critical == 1) {
                    task.C_HIGH = task.C_HIGH - (long) (total_resource_execution_time * CF);
                    task.prec_HIGH = (long) (total_resource_execution_time * CF);
                }
                task.prec_LOW = total_resource_execution_time;

                if (task.resource_required_index.size() > 0)
                    task.hasResource = 1;
            }
        }

    }

    public void PrintAllocatedSystem(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources) {
        System.out.println("----------------------------------------------------");
        if (tasks == null) {
            System.out.println("no tasks generated.");
        } else {
            for (int i = 0; i < tasks.size(); i++) {
                double util = 0;
                for (int j = 0; j < tasks.get(i).size(); j++) {
                    SporadicTask task = tasks.get(i).get(j);
                    util += ((double) (task.WCET + task.pure_resource_execution_time)) / (double) task.period;
                    System.out.println(tasks.get(i).get(j).getInfo());
                }
                System.out.println("util on partition: " + i + " : " + util);
            }
            System.out.println("----------------------------------------------------");

            if (resources != null) {
                System.out.println("****************************************************");
                for (int i = 0; i < resources.size(); i++) {
                    System.out.println(resources.get(i).toString());
                }
                System.out.println("****************************************************");

                String resource_usage = "";
                /* print resource usage */
                System.out.println("---------------------------------------------------------------------------------");
                for (int i = 0; i < tasks.size(); i++) {
                    for (int j = 0; j < tasks.get(i).size(); j++) {

                        SporadicTask task = tasks.get(i).get(j);
                        String usage = "T" + task.id + ": ";
                        for (int k = 0; k < task.resource_required_index.size(); k++) {
                            usage = usage + "R" + resources.get(task.resource_required_index.get(k)).id + " - " + task.number_of_access_in_one_release.get(k)
                                    + ";  ";
                        }
                        usage += "\n";
                        if (task.resource_required_index.size() > 0)
                            resource_usage = resource_usage + usage;
                    }
                }

                System.out.println(resource_usage);
                System.out.println("---------------------------------------------------------------------------------");
            }
        }

    }

    public void printUnallocateSystem(ArrayList<SporadicTask> tasks, ArrayList<Resource> resources) {
        System.out.println("----------------------------------------------------");
        for (int i = 0; i < tasks.size(); i++) {
            System.out.println(tasks.get(i).getInfo());
        }
        System.out.println("----------------------------------------------------");
        System.out.println("****************************************************");
        for (int i = 0; i < resources.size(); i++) {
            System.out.println(resources.get(i).toString());
        }
        System.out.println("****************************************************");

        String resource_usage = "";
        /* print resource usage */
        System.out.println("---------------------------------------------------------------------------------");
        for (int i = 0; i < tasks.size(); i++) {
            SporadicTask task = tasks.get(i);
            String usage = "T" + task.id + ": ";
            for (int k = 0; k < task.resource_required_index.size(); k++) {
                usage = usage + "R" + resources.get(task.resource_required_index.get(k)).id + " - " + task.number_of_access_in_one_release.get(k) + ";  ";
            }
            usage += "\n";
            if (task.resource_required_index.size() > 0)
                resource_usage = resource_usage + usage;

        }
        System.out.println(resource_usage);
        System.out.println("---------------------------------------------------------------------------------");

    }
}
