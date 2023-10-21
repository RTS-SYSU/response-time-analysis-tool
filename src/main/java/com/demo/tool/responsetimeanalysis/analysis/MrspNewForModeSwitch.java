package com.demo.tool.responsetimeanalysis.analysis;

import com.demo.tool.responsetimeanalysis.entity.Resource;
import com.demo.tool.responsetimeanalysis.entity.SporadicTask;
import com.demo.tool.responsetimeanalysis.utils.AnalysisUtils;

import java.util.ArrayList;
import java.util.HashSet;

public class MrspNewForModeSwitch {
    static long Cnp = 0;
    static double Cmig = 0;
    long count = 0;

    public long[][] getResponseTime(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> lowTasks, boolean printDebug) {
        long[][] init_Ri = new AnalysisUtils().initResponseTime(tasks);
        long[][] response_time = new long[tasks.size()][];
        boolean isEqual = false, missDeadline = false;
        count = 0;

        // Cnp 的初始化
        // 所有使用MrsP协议的资源中最大的csl作为npsection的值
        long npsection = 0;
        for (Resource resource : resources) {
            if (npsection < resource.csl) npsection = resource.csl;
        }
        Cnp = npsection;

        // Cmig的初始化
        Cmig = AnalysisUtils.MrsP_PREEMPTION_AND_MIGRATION;

        for (int i = 0; i < init_Ri.length; i++) {
            response_time[i] = new long[init_Ri[i].length];
        }

        new AnalysisUtils().cloneList(init_Ri, response_time);

        for (int i = 0; i < lowTasks.size(); i++) {
            for (int j = 0; j < lowTasks.get(i).size(); j++) {
                SporadicTask task = lowTasks.get(i).get(j);
                task.spin = resourceAccessingTimeForLowTask(task, resources);
            }
        }

        /* a huge busy window to get a fixed Ri */
        while (!isEqual) {
            isEqual = true;
            long[][] response_time_plus = busyWindow(tasks, resources, lowTasks, response_time, true);

            for (int i = 0; i < response_time_plus.length; i++) {
                for (int j = 0; j < response_time_plus[i].length; j++) {
                    if (response_time[i][j] != response_time_plus[i][j]) isEqual = false;

                    if (response_time_plus[i][j] > tasks.get(i).get(j).deadline) missDeadline = true;
                }
            }

            count++;
            new AnalysisUtils().cloneList(response_time_plus, response_time);

            if (missDeadline) break;
        }

        if (printDebug) {
            if (missDeadline)
                System.out.println("FIFONP JAVA    after " + count + " tims of recursion, the tasks miss the deadline.");
            else System.out.println("FIFONP JAVA    after " + count + " tims of recursion, we got the response time.");

            new AnalysisUtils().printResponseTime(response_time, tasks);
        }

        return response_time;
    }

    private long[][] busyWindow(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> lowTasks, long[][] response_time, boolean btbHit) {
        long[][] response_time_plus = new long[tasks.size()][];

        for (int i = 0; i < response_time.length; i++) {
            response_time_plus[i] = new long[response_time[i].length];
        }

        for (int i = 0; i < tasks.size(); i++) {
            for (int j = 0; j < tasks.get(i).size(); j++) {
                SporadicTask task = tasks.get(i).get(j);

                long Ei = getSpinDelay(task, tasks, lowTasks, resources, response_time[i][j], response_time);
                task.interference = highPriorityInterference(task, tasks, lowTasks, response_time[i][j]);
                task.local = localBlocking(task, tasks, lowTasks, resources, response_time[i][j], response_time, response_time[i][j]);
                long MigrateCost = getMC(task, tasks, lowTasks, resources, response_time[i][j], response_time, btbHit);
                response_time_plus[i][j] = task.Ri = task.WCET + Ei + MigrateCost + task.local + task.interference;

                if (task.Ri > task.deadline) return response_time_plus;

            }
        }
        return response_time_plus;
    }


    private long getMC(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks, ArrayList<Resource> resources, long time, long[][] Ris, boolean btbHit) {
        //double duration = task.critical == 0 ? resource.csl_low : resource.csl_high;
        long MC = getMIG(task, tasks, LowTasks, resources, time, 0, 1, time, Ris, btbHit);

        int partition = task.partition;
        ArrayList<SporadicTask> TasksPartition = tasks.get(partition);
        ArrayList<SporadicTask> LowTasksPartition = LowTasks.get(partition);
        // HI Task 部分
        for (SporadicTask hpTask : TasksPartition) {
            if (hpTask.priority > task.priority) {
                MC += getMIG(task, tasks, LowTasks, resources, (double) task.Ri, hpTask.Ri_HI, 1, time, Ris, btbHit);
            }
        }
        // LO Task 部分
        for (SporadicTask hpTask : LowTasksPartition) {
            if (hpTask.priority > task.priority) {
                MC += getMIG(task, tasks, LowTasks, resources, (double) task.Ri_LO, hpTask.Ri_LO, 0, time, Ris, btbHit);
            }
        }
        return MC;
    }

    private long getMIG(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks, ArrayList<Resource> resources, double duration, long jitter, int mode, long time, long[][] Ris, boolean btbHit) {
        long MIG = 0;
        for (int resIndex : task.resource_required_index) {
            var resource = resources.get(resIndex);
            var Nxk = task.number_of_access_in_one_release.get(task.resource_required_index.indexOf(resource.id - 1));
            var exeTime = mode == 0 ? resource.csl_low : resource.csl_high;
            for (int n = 0; n < Math.ceil((duration + jitter) / task.period) * Nxk; n++) {
                var mt = getMtSet(task, tasks, LowTasks, resource, n, time, Ris);
                MIG += getNthMIG(task, tasks, LowTasks, resource, mt, n, exeTime, time, Ris, btbHit);
            }
        }
        return MIG;
    }

    private long getNthMIG(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks, Resource resource, HashSet<Integer> partitionSet, int nth, double exeTime, long time, long[][] Ris, boolean btbHit) {
        long MIG = 0;


        MIG += getMig(task, tasks, LowTasks, partitionSet, resource, exeTime);

        //遍历所有partition
        for (int i = 0; i < tasks.size(); i++) {
            if (task.partition != i) {
                if (getNumberOfRemoteTask(task, tasks, LowTasks, resource, i, time, Ris) >= getNumberOfLocalHighPriorityTask(task, tasks, LowTasks, resource, time, Ris) + nth) {
                    var RBTQs = getRBTQs(task, tasks, LowTasks, resource, time, Ris);
                    if ( nth + getNumberOfLocalHighPriorityTask(task, tasks, LowTasks, resource, time, Ris) < RBTQs.get(i).size())
                        MIG += getMig(task, tasks, LowTasks, partitionSet, resource, RBTQs.get(i).get((int) (nth + getNumberOfLocalHighPriorityTask(task, tasks, LowTasks, resource, time, Ris))));
                }
            }
        }

        return MIG;
    }

    private long getMig(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks, HashSet<Integer> partitionSet, Resource resource, double exeTime) {
        long mig = 0;
        var mt = partitionSet;
        var mtp = getMtpSet(tasks, LowTasks, resource, mt);
        for (int partition : mt) {
            double cost = 0;
            if ((mt.size() == 1 && mt.contains(partition)) || !mtp.contains(partition)) {
                cost = 0;
            } else if (mt.size() > 1 && (mtp.size() == 1 && mtp.contains(partition))) {
                cost = 2 * Cmig;
            } else {
                var Mnp = getMnp(exeTime);
                var Mhp = MhpBusyWindow(task, tasks, mt, exeTime, resource, Mnp);
                cost = (long) Math.min(Mhp, Mnp);
            }
            mig += cost;
        }
        return mig;
    }

    private double MhpBusyWindow(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, HashSet<Integer> migration_targets_with_P, double duration, Resource resource, double migByNP) {
        double migCost = 0;


        double newMigCost = MhpOneCal(migration_targets_with_P, duration + migCost, resource, tasks);

        while (migCost != newMigCost) {
            migCost = newMigCost;
            newMigCost = MhpOneCal(migration_targets_with_P, duration + migCost, resource, tasks);

            if (newMigCost > task.deadline) {
                return newMigCost;
            }
            if (migByNP > 0 && newMigCost > migByNP) {
                return newMigCost;
            }
        }

        return migCost;
    }

    private double MhpOneCal(HashSet<Integer> migration_targets_with_P, double duration, Resource resource, ArrayList<ArrayList<SporadicTask>> tasks) {
        double migCost = 0;

        for (int partition_with_p : migration_targets_with_P) {

            for (int j = 0; j < tasks.get(partition_with_p).size(); j++) {
                SporadicTask hpTask = tasks.get(partition_with_p).get(j);

                if (hpTask.priority > resource.getCeilingForProcessor(tasks, partition_with_p))
                    migCost += Math.ceil((duration) / hpTask.period) * Cmig;

            }
        }

        return migCost + Cmig;
    }

    public double getMnp(double duration) {
        return Cmig * (Math.ceil(duration / Cnp) + 1);
    }

    /**
     * @param task:
     * @param tasks:
     * @param LowTasks:
     * @param resource:
     * @param nth:
     * @param time:
     * @param Ris:
     * @return java.util.HashSet<java.lang.Integer>
     * @description: 在关键模式切换过程中，任务 τx 在发布期间第 n 次访问资源 rk 时可能的迁移目标处理器集合
     * @author: Chen
     * @date: 2023/10/15 15:28
     */
    HashSet<Integer> getMtSet(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks, Resource resource, int nth, long time, long[][] Ris) {
        var mt = new HashSet<Integer>();
        mt.add(task.partition);
        // 遍历所有remote partition
        for (int i = 0; i < tasks.size(); i++) {
            if (task.partition != i) {
                if (getNumberOfLocalHighPriorityTask(task, tasks, LowTasks, resource, time, Ris) - getNumberOfRemoteTask(task, tasks, LowTasks, resource, i, time, Ris) - nth + 1 > 0)
                    mt.add(i);
            }
        }
        return mt;
    }

    /**
     * @param tasks:
     * @return java.util.ArrayList<java.lang.Integer>
     * @description: 给定迁移目标集合 mt 和资源 rk, 可能存在访问资源时抢占的处理器集合
     * @author: Chen
     * @date: 2023/10/10 17:24
     */
    private ArrayList<Integer> getMtpSet(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks, Resource resource, HashSet<Integer> mt) {
        ArrayList<Integer> mtp = new ArrayList<Integer>();
        for (int partition : mt) {
            int ceiling = Integer.max(resource.getCeilingForProcessor(tasks, partition), resource.getCeilingForProcessor(LowTasks, partition));
            if (tasks.get(partition).size() != 0) {
                if (tasks.get(partition).get(0).priority > ceiling)   // 保证优先级降序
                {
                    mtp.add(partition);
                    break;
                }
            }
            if (LowTasks.get(partition).size() != 0) {
                if (LowTasks.get(partition).get(0).priority > ceiling)   // 保证优先级降序
                {
                    mtp.add(partition);
                    break;
                }
            }
        }
        return mtp;
    }

    private Long getNumberOfLocalHighPriorityTask(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks, Resource resource, long time, long[][] Ris) {
        long ncs_lo = 0;
        long ncs_hi = 0;

        // 本地高优先级任务访问资源的次数，LowTask部分
        for (int i = 0; i < LowTasks.get(task.partition).size(); i++) {
            SporadicTask hpTask = LowTasks.get(task.partition).get(i);
            if (hpTask.priority > task.priority && hpTask.resource_required_index.contains(resource.id - 1)) {
                int n_j_k = hpTask.number_of_access_in_one_release.get(hpTask.resource_required_index.indexOf(resource.id - 1));
                ncs_lo += (long) Math.ceil((double) (task.Ri_LO + hpTask.Ri_LO) / (double) hpTask.period) * n_j_k;
            }
        }
        // 本地高优先级任务访问资源的次数，HiTask部分
        for (int i = 0; i < tasks.get(task.partition).size(); i++) {
            SporadicTask hpTask = tasks.get(task.partition).get(i);
            if (hpTask.priority > task.priority && hpTask.resource_required_index.contains(resource.id - 1)) {
                int n_h_k = hpTask.number_of_access_in_one_release.get(hpTask.resource_required_index.indexOf(resource.id - 1));
                ncs_hi += (long) Math.ceil((double) (time + Ris[hpTask.partition][i]) / (double) hpTask.period) * n_h_k;
            }
        }

        return ncs_lo + ncs_hi;
    }

    private Long getNumberOfRemoteTask(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks, Resource resource, int partition, long time, long[][] Ris) {
        long number_of_high_request_by_Remote_P = 0, number_of_low_request_by_Remote_P = 0;

        // 远程处理器HI任务访问资源次数
        for (int j = 0; j < tasks.get(partition).size(); j++) {
            if (tasks.get(partition).get(j).resource_required_index.contains(resource.id - 1)) {
                SporadicTask remote_task = tasks.get(partition).get(j);
                int indexR = getIndexRInTask(remote_task, resource);
                long number_of_release_hi = (long) Math.ceil((double) (time + Ris[partition][j]) / (double) remote_task.period);
                number_of_high_request_by_Remote_P += number_of_release_hi * remote_task.number_of_access_in_one_release.get(indexR);
            }
        }
        // 远程处理器LO任务访问资源次数
        for (int j = 0; j < LowTasks.get(partition).size(); j++) {
            if (LowTasks.get(partition).get(j).resource_required_index.contains(resource.id - 1)) {
                SporadicTask remote_task = LowTasks.get(partition).get(j);
                int indexR = getIndexRInTask(remote_task, resource);
                long number_of_release_lo = (long) Math.ceil((double) (task.Ri_LO + remote_task.Ri_LO) / (double) remote_task.period);
                number_of_low_request_by_Remote_P += number_of_release_lo * remote_task.number_of_access_in_one_release.get(indexR);
            }
        }

        return number_of_high_request_by_Remote_P + number_of_low_request_by_Remote_P;

    }


    private long resourceAccessingTimeForLowTask(SporadicTask t, ArrayList<Resource> resources) {
        long spin_delay = 0;
        for (int k = 0; k < t.resource_required_index.size(); k++) {
            Resource resource = resources.get(t.resource_required_index.get(k));
            spin_delay += resource.partitions.size() * resource.csl_low * t.number_of_access_in_one_release.get(k);
        }
        return spin_delay;
    }

    /*
     * Calculate the spin delay for a given task t.
     */
    private long getSpinDelay(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks, ArrayList<Resource> resources, long time, long[][] Ris) {
        long spin = 0;
        long indirect_spin = 0, direct_spin = 0;
        long PWLP_S = 0;
        long exec_and_preempted_T = 0;

        ArrayList<ArrayList<ArrayList<Long>>> requestsLeftOnRemoteP = new ArrayList<>();
        for (int i = 0; i < resources.size(); i++) {
            requestsLeftOnRemoteP.add(new ArrayList<>());
            Resource res = resources.get(i);
            ArrayList<Long> temp = getSpinDelayForOneResource(task, tasks, LowTasks, res, time, Ris, requestsLeftOnRemoteP.get(i));
            indirect_spin += temp.get(0);
            direct_spin += temp.get(1);
            exec_and_preempted_T += temp.get(2);
        }

        task.indirect_spin = indirect_spin;
        task.spin = direct_spin;

        return exec_and_preempted_T;
    }

    private ArrayList<Long> getSpinDelayForOneResource(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks, Resource resource, long time, long[][] Ris, ArrayList<ArrayList<Long>> requestsLeftOnRemoteP) {
        long spin = 0;
        long ncs = 0;
        long ncs_lo = 0;
        long ncs_hi = 0;
        long N_i_k = 0;

        long indirect_spin = 0;
        long direct_spin = 0;

        // 任务自身访问资源的次数
        if (task.resource_required_index.contains(resource.id - 1)) {
            N_i_k += task.number_of_access_in_one_release.get(task.resource_required_index.indexOf(resource.id - 1));
        }


        // 本地高优先级任务访问资源的次数，LowTask部分
        for (int i = 0; i < LowTasks.get(task.partition).size(); i++) {
            SporadicTask hpTask = LowTasks.get(task.partition).get(i);
            if (hpTask.priority > task.priority && hpTask.resource_required_index.contains(resource.id - 1)) {
                int n_j_k = hpTask.number_of_access_in_one_release.get(hpTask.resource_required_index.indexOf(resource.id - 1));
                ncs_lo += (long) Math.ceil((double) (task.Ri_LO + hpTask.Ri_LO) / (double) hpTask.period) * n_j_k;
            }
        }
        // 本地高优先级任务访问资源的次数，HiTask部分
        for (int i = 0; i < tasks.get(task.partition).size(); i++) {
            SporadicTask hpTask = tasks.get(task.partition).get(i);
            if (hpTask.priority > task.priority && hpTask.resource_required_index.contains(resource.id - 1)) {
                int n_h_k = hpTask.number_of_access_in_one_release.get(hpTask.resource_required_index.indexOf(resource.id - 1));
                ncs_hi += (long) Math.ceil((double) (time + Ris[hpTask.partition][i]) / (double) hpTask.period) * n_h_k;
            }
        }

        ncs = N_i_k + ncs_lo + ncs_hi;

        // RBTQ项的计算
        if (ncs > 0) {
            for (int i = 0; i < tasks.size(); i++) {
                // 遍历所有远程处理器
                if (task.partition != i) {
                    /* For each remote partition */
                    long number_of_low_request_by_Remote_P = 0;
                    long number_of_high_request_by_Remote_P = 0;
                    // 远程处理器HI任务访问资源次数
                    for (int j = 0; j < tasks.get(i).size(); j++) {
                        if (tasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
                            SporadicTask remote_task = tasks.get(i).get(j);
                            int indexR = getIndexRInTask(remote_task, resource);
                            long number_of_release_hi = (long) Math.ceil((double) (time + Ris[i][j]) / (double) remote_task.period);
                            number_of_high_request_by_Remote_P += number_of_release_hi * remote_task.number_of_access_in_one_release.get(indexR);
                        }
                    }
                    // 远程处理器LO任务访问资源次数
                    for (int j = 0; j < LowTasks.get(i).size(); j++) {
                        if (LowTasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
                            SporadicTask remote_task = LowTasks.get(i).get(j);
                            int indexR = getIndexRInTask(remote_task, resource);
                            long number_of_release_lo = (long) Math.ceil((double) (task.Ri_LO + remote_task.Ri_LO) / (double) remote_task.period);
                            number_of_low_request_by_Remote_P += number_of_release_lo * remote_task.number_of_access_in_one_release.get(indexR);
                        }
                    }

                    long possible_spin_delay = Long.min(number_of_high_request_by_Remote_P + number_of_low_request_by_Remote_P, ncs);

                    //min{local, remote m}
                    long indirect_remote_times = number_of_high_request_by_Remote_P - ncs;
                    indirect_spin += indirect_remote_times > 0 ? ncs * resource.csl_high : number_of_high_request_by_Remote_P * resource.csl_high + Long.min(Math.abs(indirect_remote_times), number_of_low_request_by_Remote_P) * resource.csl_low;

                    if (number_of_high_request_by_Remote_P > ncs) {
                        indirect_spin += ncs * resource.csl_high;
                    } else {
                        indirect_spin += number_of_high_request_by_Remote_P * resource.csl_high + Long.min(Math.abs(number_of_high_request_by_Remote_P - ncs), number_of_low_request_by_Remote_P) * resource.csl_low;
                    }
                    //min{N, max(remote_m-local_higher,0)}
//                    long direct_remote_times_judge = Long.max(indirect_remote_times, 0) + Long.min(N_i_k, Long.max(number_of_high_request_by_Remote_P + number_of_low_request_by_Remote_P - ncs, 0))
//                            - number_of_high_request_by_Remote_P;
//                    direct_spin += direct_remote_times_judge > 0 ?
//                            Long.min(direct_remote_times_judge, number_of_low_request_by_Remote_P) * resource.csl
//                            : Long.min(Math.abs(direct_remote_times_judge), number_of_high_request_by_Remote_P) * resource.csl_high;

                    if (number_of_high_request_by_Remote_P + number_of_low_request_by_Remote_P - ncs <= 0)
                        direct_spin += 0;
                    else if (indirect_remote_times <= 0)
                        direct_spin += Long.min(Long.max(number_of_high_request_by_Remote_P + number_of_low_request_by_Remote_P - ncs, 0), N_i_k) * resource.csl_low;
                    else
                        direct_spin += indirect_remote_times > N_i_k ? N_i_k * resource.csl_high : indirect_remote_times * resource.csl_high + Long.min(N_i_k - indirect_remote_times, number_of_low_request_by_Remote_P) * resource.csl_low;


                    // 建立RBTQ
                    ArrayList<Long> RBTQ = new ArrayList<>();
                    while (number_of_high_request_by_Remote_P > 0) {
                        RBTQ.add(resource.csl_high);
                        number_of_high_request_by_Remote_P--;
                    }
                    //min{local, remote m}
                    while (number_of_low_request_by_Remote_P > 0) {
                        RBTQ.add(resource.csl_low);
                        number_of_low_request_by_Remote_P--;
                    }


                    while (possible_spin_delay > 0) {
                        spin += RBTQ.remove(0);
                        possible_spin_delay--;
                    }
                    // 这个处理器还剩余次数的话加入
                    if (RBTQ.size() > 0) requestsLeftOnRemoteP.add(RBTQ);
                }
            }
        }
        //<indirect,direct, preempted+exec>
        ArrayList<Long> spin_all = new ArrayList<>();
        spin_all.add(indirect_spin);
        spin_all.add(direct_spin);
        spin_all.add(N_i_k * resource.csl_high + ncs_lo * resource.csl_low + ncs_hi * resource.csl_high);

        return spin_all;
    }


    /*
     * Calculate interference for a given task t.
     * Including HI tasks and LO tasks
     */
    private long highPriorityInterference(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks, long time) {
        long interference = 0;
        int partition = t.partition;
        ArrayList<SporadicTask> TasksPartition = tasks.get(partition);
        ArrayList<SporadicTask> LowTasksPartition = LowTasks.get(partition);
        // HI Task 部分
        for (SporadicTask hpTask : TasksPartition) {
            if (hpTask.priority > t.priority) {
                interference += Math.ceil((double) (time) / (double) hpTask.period) * hpTask.WCET;
            }
        }
        // LO Task 部分
        for (SporadicTask hpTask : LowTasksPartition) {
            if (hpTask.priority > t.priority) {
                interference += Math.ceil((double) (t.Ri_LO) / (double) hpTask.period) * hpTask.WCET;
            }
        }
        return interference;
    }

    private long localBlocking(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> lowtasks, ArrayList<Resource> resources, long time, long[][] Ris, long Ri) {
        ArrayList<SporadicTask> localTask = tasks.get(t.partition);
        localTask.addAll(lowtasks.get(t.partition));
        ArrayList<Resource> LocalBlockingResources_LO = getLocalBlockingResources_LO(t, resources, localTask);
        ArrayList<Resource> LocalBlockingResources_HI = getLocalBlockingResources_HI(t, resources, localTask);
        ArrayList<Long> local_blocking_each_resource = new ArrayList<>();

        for (int i = 0; i < LocalBlockingResources_LO.size(); i++) {
            HashSet<Integer> migration_targets = new HashSet<>();

            Resource res = LocalBlockingResources_LO.get(i);
            long local_blocking = res.csl_low;

            migration_targets.add(t.partition);
            if (res.isGlobal) {
                int remoteblocking = 0;
                for (int parition_index = 0; parition_index < res.partitions.size(); parition_index++) {
                    int partition = res.partitions.get(parition_index);

                    var norHP = getNumberOfLocalHighPriorityTask(t, tasks, lowtasks, res, time, Ris);
                    int norT = t.resource_required_index.contains(res.id - 1)
                            ? t.number_of_access_in_one_release.get(t.resource_required_index.indexOf(res.id - 1))
                            : 0;

                    var norR_HI = getNumberOfRemoteTask(t, tasks, null, res, parition_index, time, Ris);
                    var norR_LO = getNumberOfRemoteTask(t, null, lowtasks, res, parition_index, time, Ris);

                    if (partition != t.partition && (norHP + norT) < norR_HI + norR_LO) {
                        if (norHP + norT < norR_HI)
                            local_blocking += res.csl_high;
                        else
                            local_blocking += res.csl_low;
                        remoteblocking++;
                        migration_targets.add(partition);
                    }
                }

                double mc_plus = 0;
                if (Cmig != 0) {
                    double mc = getMig(t, tasks, lowtasks, migration_targets, res, res.csl_low);

                    long mc_long = (long) Math.floor(mc);
                    mc_plus += mc - mc_long;
                    if (mc - mc_long < 0) {
                        System.err.println("MrsP mig error");
                        System.exit(-1);
                    }
                    local_blocking += mc_long;
                }

            }

            local_blocking_each_resource.add(local_blocking);

        }


        for (int i = 0; i < LocalBlockingResources_HI.size(); i++) {
            HashSet<Integer> migration_targets = new HashSet<>();

            Resource res = LocalBlockingResources_HI.get(i);
            long local_blocking = res.csl_high;

            migration_targets.add(t.partition);
            if (res.isGlobal) {
                int remoteblocking = 0;
                for (int parition_index = 0; parition_index < res.partitions.size(); parition_index++) {
                    int partition = res.partitions.get(parition_index);
                    var norHP = getNumberOfLocalHighPriorityTask(t, tasks, lowtasks, res, time, Ris);
                    int norT = t.resource_required_index.contains(res.id - 1)
                            ? t.number_of_access_in_one_release.get(t.resource_required_index.indexOf(res.id - 1))
                            : 0;

                    var norR_HI = getNumberOfRemoteTask(t, tasks, null, res, parition_index, time, Ris);
                    var norR_LO = getNumberOfRemoteTask(t, null, lowtasks, res, parition_index, time, Ris);

                    if (partition != t.partition && (norHP + norT) < norR_HI + norR_LO) {
                        if (norHP + norT < norR_HI)
                            local_blocking += res.csl_high;
                        else
                            local_blocking += res.csl_low;
                        remoteblocking++;
                        migration_targets.add(partition);
                    }
                }
                double mc_plus = 0;
                if (Cmig != 0) {
                    double mc = getMig(t, tasks, lowtasks, migration_targets, res, res.csl_high);

                    long mc_long = (long) Math.floor(mc);
                    mc_plus += mc - mc_long;
                    if (mc - mc_long < 0) {
                        System.err.println("MrsP mig error");
                        System.exit(-1);
                    }
                    local_blocking += mc_long;
                }
            }

            local_blocking_each_resource.add(local_blocking);
        }

        if (local_blocking_each_resource.size() > 1)
            local_blocking_each_resource.sort((l1, l2) -> -Long.compare(l1, l2));

        return local_blocking_each_resource.size() > 0 ? local_blocking_each_resource.get(0) : 0;
    }

    private ArrayList<Resource> getLocalBlockingResources_HI(SporadicTask task, ArrayList<Resource> resources, ArrayList<SporadicTask> localTasks) {
        ArrayList<Resource> localBlockingResources = new ArrayList<>();
        int partition = task.partition;

        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);

            if (resource.partitions.contains(partition) && resource.getCeilingForProcessor(localTasks) >= task.priority) {
                for (int j = 0; j < resource.requested_tasks.size(); j++) {
                    SporadicTask LP_task = resource.requested_tasks.get(j);
                    if (LP_task.critical == 1 && LP_task.partition == partition && LP_task.priority < task.priority) {
                        localBlockingResources.add(resource);
                        break;
                    }
                }
            }
        }

        return localBlockingResources;
    }

    private ArrayList<Resource> getLocalBlockingResources_LO(SporadicTask task, ArrayList<Resource> resources, ArrayList<SporadicTask> localTasks) {
        ArrayList<Resource> localBlockingResources = new ArrayList<>();
        int partition = task.partition;

        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);

            if (resource.partitions.contains(partition) && resource.getCeilingForProcessor(localTasks) >= task.priority) {
                for (int j = 0; j < resource.requested_tasks.size(); j++) {
                    SporadicTask LP_task = resource.requested_tasks.get(j);
                    if (LP_task.critical == 0 && LP_task.partition == partition && LP_task.priority < task.priority) {
                        localBlockingResources.add(resource);
                        break;
                    }
                }
            }
        }

        return localBlockingResources;
    }


    /*
     * Return Sum (Pm!=P(ti)) RBTQ(ncs)
     */
    private long getRemoteBlockingTime(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks, ArrayList<Resource> resources, long time, long[][] Ris) {
        long BlockingTime = 0;

        for (int i = 0; i < resources.size(); i++) {
            Resource res = resources.get(i);
            ArrayList<ArrayList<Long>> RBTQs = getRBTQs(task, tasks, LowTasks, res, time, Ris);
            long ncs = getNcs(task, tasks, LowTasks, res, time, Ris);
            // Sum (Pm!=P(ti)) RBTQ(ncs)
            for (int m = 0; m < tasks.size(); m++) {
                if (task.partition != m) {
                    var RBTQ = RBTQs.get(m);
                    BlockingTime += RBTQ.get((int) ncs);
                }
            }
        }
        return BlockingTime;
    }

    /*
     * getRBTQs返回在模式切换过程中，在 ti 响应时间内，所有remote processor上访问资源rk 的远程阻塞时间队列（RBTQs）,local processor 为 null
     * RBTQ表示在模式切换过程中，在 ti 响应时间内，remote processor m 上访问资源rk 的远程阻塞时间队列（RBTQ）
     */
    private ArrayList<ArrayList<Long>> getRBTQs(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks, Resource resource, long time, long[][] Ris) {

        ArrayList<ArrayList<Long>> RBTQs = new ArrayList<>();
        long number_of_request_by_Remote_P = 0;
        // RBTQ项的计算
        for (int i = 0; i < tasks.size(); i++) {
            // 遍历所有远程处理器
            if (task.partition != i) {
                /* For each remote partition */
                long number_of_low_request_by_Remote_P = 0;
                long number_of_high_request_by_Remote_P = 0;
                // 远程处理器HI任务访问资源次数
                for (int j = 0; j < tasks.get(i).size(); j++) {
                    if (tasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
                        SporadicTask remote_task = tasks.get(i).get(j);
                        int indexR = getIndexRInTask(remote_task, resource);
                        long number_of_release_hi = (long) Math.ceil((double) (time + Ris[i][j]) / (double) remote_task.period);
                        number_of_high_request_by_Remote_P += number_of_release_hi * remote_task.number_of_access_in_one_release.get(indexR);
                    }
                }
                // 远程处理器LO任务访问资源次数
                for (int j = 0; j < LowTasks.get(i).size(); j++) {
                    if (LowTasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
                        SporadicTask remote_task = LowTasks.get(i).get(j);
                        int indexR = getIndexRInTask(remote_task, resource);
                        long number_of_release_lo = (long) Math.ceil((double) (task.Ri_LO + remote_task.Ri_LO) / (double) remote_task.period);
                        number_of_low_request_by_Remote_P += number_of_release_lo * remote_task.number_of_access_in_one_release.get(indexR);
                    }
                }

                // 建立RBTQ
                var RBTQ = new ArrayList<Long>();
                while (number_of_high_request_by_Remote_P > 0) {
                    RBTQ.add(resource.csl_high);
                    number_of_high_request_by_Remote_P--;
                }
                //min{local, remote m}
                while (number_of_low_request_by_Remote_P > 0) {
                    RBTQ.add(resource.csl_low);
                    number_of_low_request_by_Remote_P--;
                }
                RBTQs.add(RBTQ);
            } else {
                RBTQs.add(new ArrayList<Long>());
            }

        }
        return RBTQs;
    }

    /*
     * 返回在模式切换过程中，local processor 上的高优先级任务在 τi 响应时间内访问资源 rk 的次数 + 任务 τi 执行资源 rk 的次数
     */
    private Long getNcs(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks, Resource resource, long time, long[][] Ris) {
        long ncs = 0;
        long ncs_lo = 0;
        long ncs_hi = 0;
        long N_i_k = 0;

        // 任务自身访问资源的次数
        if (task.resource_required_index.contains(resource.id - 1)) {
            N_i_k += task.number_of_access_in_one_release.get(task.resource_required_index.indexOf(resource.id - 1));
        }


        // 本地高优先级任务访问资源的次数，LowTask部分
        for (int i = 0; i < LowTasks.get(task.partition).size(); i++) {
            SporadicTask hpTask = LowTasks.get(task.partition).get(i);
            if (hpTask.priority > task.priority && hpTask.resource_required_index.contains(resource.id - 1)) {
                int n_j_k = hpTask.number_of_access_in_one_release.get(hpTask.resource_required_index.indexOf(resource.id - 1));
                ncs_lo += (long) Math.ceil((double) (task.Ri_LO + hpTask.Ri_LO) / (double) hpTask.period) * n_j_k;
            }
        }
        // 本地高优先级任务访问资源的次数，HiTask部分
        for (int i = 0; i < tasks.get(task.partition).size(); i++) {
            SporadicTask hpTask = tasks.get(task.partition).get(i);
            if (hpTask.priority > task.priority && hpTask.resource_required_index.contains(resource.id - 1)) {
                int n_h_k = hpTask.number_of_access_in_one_release.get(hpTask.resource_required_index.indexOf(resource.id - 1));
                ncs_hi += (long) Math.ceil((double) (time + Ris[hpTask.partition][i]) / (double) hpTask.period) * n_h_k;
            }
        }

        ncs = N_i_k + ncs_lo + ncs_hi;
        return ncs;
    }

    /*
     * Return the index of a given resource in stored in a task.
     */
    private int getIndexRInTask(SporadicTask task, Resource resource) {
        int indexR = -1;
        if (task.resource_required_index.contains(resource.id - 1)) {
            for (int j = 0; j < task.resource_required_index.size(); j++) {
                if (resource.id - 1 == task.resource_required_index.get(j)) {
                    indexR = j;
                    break;
                }
            }
        }
        return indexR;
    }
}
