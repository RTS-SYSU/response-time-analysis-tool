package com.demo.tool.responsetimeanalysis.analysis;

import  com.demo.tool.responsetimeanalysis.entity.Resource;
import  com.demo.tool.responsetimeanalysis.entity.SporadicTask;
import  com.demo.tool.responsetimeanalysis.generator.PriorityGenerator;
import  com.demo.tool.responsetimeanalysis.utils.AnalysisUtils;

import java.util.*;
import java.util.stream.Collectors;

public class DynamicAnalysisForModeSwitch {
    public long[][] getResponseTimeByDMPO(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> lowTasks,
                                          int extendCal, boolean testSchedulability,
                                          boolean btbHit, boolean useRi, boolean useDM, boolean printDebug) {
        if (tasks == null)
            return null;

        // 在此处赋予优先级
        if (useDM) {
            // assign priorities by Deadline Monotonic
            tasks = new PriorityGenerator().assignPrioritiesByDM(tasks);
        } else {
            for (int i = 0; i < tasks.size(); i++) {
                tasks.get(i).sort((t1, t2) -> -Integer.compare(t1.priority, t2.priority));
            }
        }

        long count = 0; // The number of calculations
        long np = 0; // The NP section length if MrsP is applied

        // 所有使用MrsP协议的资源中最大的csl作为npsection的值
        // high任务：csl=csl_high  low任务为 csl_low
        long npsection = 0;
        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);
            if ((resource.protocol == 6 || resource.protocol == 7) && npsection < resource.csl)
                npsection = resources.get(i).csl;
        }
        np = npsection;

        long[][] init_Ri = new AnalysisUtils().initResponseTime(tasks);
        long[][] response_time = new long[tasks.size()][];
        boolean isEqual = false, missdeadline = false;
        count = 0;

        for (int i = 0; i < init_Ri.length; i++) {
            response_time[i] = new long[init_Ri[i].length];
        }

        new AnalysisUtils().cloneList(init_Ri, response_time);

        ArrayList<ArrayList<ArrayList<Long>>> history = new ArrayList<>();

        /* a huge busy window to get a fixed Ri */
        while (!isEqual) {
            isEqual = true;
            boolean should_finish = true;
            long[][] response_time_plus = busyWindow(tasks, resources, lowTasks, response_time, AnalysisUtils.MrsP_PREEMPTION_AND_MIGRATION, np, extendCal,
                    testSchedulability, btbHit, useRi);


            for (int i = 0; i < response_time_plus.length; i++) {
                for (int j = 0; j < response_time_plus[i].length; j++) {
                    if (response_time[i][j] != response_time_plus[i][j])
                        isEqual = false;
                    if (testSchedulability) {
                        if (response_time_plus[i][j] > tasks.get(i).get(j).deadline)
                            missdeadline = true;
                    } else {
                        if (response_time_plus[i][j] <= tasks.get(i).get(j).deadline * extendCal)
                            should_finish = false;
                    }
                }
            }

            count++;
            new AnalysisUtils().cloneList(response_time_plus, response_time);

            if (testSchedulability) {
                if (missdeadline)
                    break;
            } else {
                if (should_finish)
                    break;
            }
        }

        if (printDebug) {
            System.out.println("FIFO Spin Locks Framework    after " + count + " tims of recursion, we got the response time.");
            new AnalysisUtils().printResponseTime(response_time, tasks);
        }

        return response_time;
    }

    private long[][] busyWindow(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> lowTasks,
                                long[][] response_time, double oneMig, long np,
                                int extendCal, boolean testSchedulability, boolean btbHit, boolean useRi) {
        long[][] response_time_plus = new long[tasks.size()][];

        for (int i = 0; i < response_time.length; i++) {
            response_time_plus[i] = new long[response_time[i].length];
        }

        for (int i = 0; i < tasks.size(); i++) {
            for (int j = 0; j < tasks.get(i).size(); j++) {
                SporadicTask task = tasks.get(i).get(j);
                if (response_time[i][j] > task.deadline * extendCal) {
                    response_time_plus[i][j] = response_time[i][j];
                    continue;
                }

                response_time_plus[i][j] = oneCalculation(task, tasks, resources, lowTasks, response_time, response_time[i][j], oneMig, np, btbHit, useRi);

                if (testSchedulability && task.Ri > task.deadline) {
                    return response_time_plus;
                }
            }
        }
        return response_time_plus;
    }

    private long oneCalculation(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> lowTasks,
                                long[][] response_time, long Ri,
                                double oneMig, long np, boolean btbHit, boolean useRi) {

        task.Ri = task.spin = task.interference = task.local = task.indirect_spin = task.total_blocking = 0;
        task.np_section = task.blocking_overheads = task.implementation_overheads = task.migration_overheads_plus = 0;
        task.mrsp_arrivalblocking_overheads = task.fifonp_arrivalblocking_overheads = task.fifop_arrivalblocking_overheads = 0;
        task.test_delay = 0;

        task.implementation_overheads += AnalysisUtils.FULL_CONTEXT_SWTICH1;

        task.spin = resourceAccessingTime(task, tasks, resources, lowTasks, response_time, Ri, oneMig, np, btbHit, useRi, task);
        task.interference = highPriorityInterference(task, tasks, resources, lowTasks, response_time, Ri, oneMig, np, btbHit, useRi);
        task.local = localBlocking(task, tasks, resources, lowTasks, response_time, Ri, oneMig, np, btbHit, useRi);

        long implementation_overheads = (long) Math.ceil(task.implementation_overheads + task.migration_overheads_plus);
        long newRi = task.Ri = task.WCET + task.spin + task.interference + task.local + implementation_overheads;

        task.total_blocking = task.spin + task.indirect_spin + task.local - task.pure_resource_execution_time + (long) Math.ceil(task.blocking_overheads);
        if (task.total_blocking < 0) {
            System.err.println("total blocking error: T" + task.id + "   total blocking: " + task.total_blocking);
            System.exit(-1);
        }

        return newRi;
    }

    /***************************************************
     ************* Direct Spin Delay *******************
     ***************************************************/
    private long resourceAccessingTime(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> lowTasks,
                                       long[][] Ris, long time,
                                       double oneMig, long np, boolean btbHit, boolean useRi, SporadicTask calT) {
        long resourceTime = 0;
        resourceTime += FIFONPResourceTime(task, tasks, resources, lowTasks, Ris, time, btbHit, useRi);
        resourceTime += FIFOPResourceAccessTime(task, tasks, resources, lowTasks, Ris, time, btbHit, useRi);
        resourceTime += MrsPresourceAccessingTime(task, tasks, resources, lowTasks, Ris, time, 0, oneMig, np, btbHit, useRi, calT);
        return resourceTime;
    }

    /**
     * FIFO-NP resource accessing time.
     */
    private long FIFONPResourceTime(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> lowTasks,
                                    long[][] Ris, long Ri,
                                    boolean btbHit, boolean useRi) {
        long spin_delay = 0;
        for (int k = 0; k < t.resource_required_index.size(); k++) {
            Resource resource = resources.get(t.resource_required_index.get(k));
            if (resource.protocol == 1) {
                //long NoS = getNoSpinDelay(t, resource, tasks, lowTasks, Ris, Ri, btbHit, useRi);
                ArrayList<Integer> NoS = getNoSpinDelay(t, resource, tasks, lowTasks, Ris, Ri, btbHit, useRi);
                spin_delay += NoS.get(0) * resource.csl_high + NoS.get(1) * resource.csl_low;
                //spin_delay += NoS;

                t.implementation_overheads += (NoS.get(0)+NoS.get(1) + t.number_of_access_in_one_release.get(t.resource_required_index.indexOf(resource.id - 1)))
                        * (AnalysisUtils.FIFONP_LOCK + AnalysisUtils.FIFONP_UNLOCK);
                t.blocking_overheads += (NoS.get(0)+NoS.get(1)) * (AnalysisUtils.FIFONP_LOCK + AnalysisUtils.FIFONP_UNLOCK);

                spin_delay += resource.csl_high * t.number_of_access_in_one_release.get(t.resource_required_index.indexOf(resource.id - 1));
            }

        }
        return spin_delay;
    }

    /*
     * gives the number of requests from remote partitions for a resource that
     * is required by the given task.
     * 当前分析任务访问收到的远程阻塞
     */
    private ArrayList<Integer> getNoSpinDelay(SporadicTask task, Resource resource, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> lowTasks,
                               long[][] Ris, long Ri, boolean btbHit,
                               boolean useRi) {
        int number_of_spin_dealy = 0;
        int number_of_spin_high = 0;
        int number_of_spin_low =0;

        for (int i = 0; i < tasks.size(); i++) {
            if (i != task.partition) {
                /* For each remote partition */
                int number_of_request_by_Remote_P = 0;
                for (int j = 0; j < tasks.get(i).size(); j++) {
                    if (tasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
                        SporadicTask remote_task = tasks.get(i).get(j);
                        int indexR = getIndexRInTask(remote_task, resource);
                        int number_of_release = (int) Math
                                .ceil((double) (Ri + (btbHit ? (useRi ? Ris[i][j] : remote_task.deadline) : 0)) / (double) remote_task.period);
                        number_of_request_by_Remote_P += number_of_release * remote_task.number_of_access_in_one_release.get(indexR);
                    }
                }
                //本地高优先级任务
                int getNoRFromHP = getNoRFromHP(resource, task, tasks.get(task.partition), Ris[task.partition], Ri, btbHit, useRi, false) +
                        getNoRFromHP(resource, task, lowTasks.get(task.partition), Ris[task.partition], Ri, btbHit, useRi, true);   //high priority low task

                // remote high task blocking
                int possible_spin_delay = number_of_request_by_Remote_P - getNoRFromHP < 0 ? 0 : number_of_request_by_Remote_P - getNoRFromHP;
                int NoRFromT = task.number_of_access_in_one_release.get(getIndexRInTask(task, resource));
                getNoRFromHP = number_of_request_by_Remote_P - getNoRFromHP < 0 ? getNoRFromHP - number_of_request_by_Remote_P : 0;

                number_of_spin_dealy += Integer.min(possible_spin_delay, NoRFromT) * resource.csl_high;
                number_of_spin_high += Integer.min(possible_spin_delay, NoRFromT);

                NoRFromT -= possible_spin_delay;
                if (NoRFromT <= 0)
                    continue;

                // for remote low task
                int number_of_request_by_RemoteLowTask_P = 0;
                for (int j = 0; j < lowTasks.get(i).size(); j++) {
                    if (lowTasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
                        SporadicTask remote_task = lowTasks.get(i).get(j);
                        int indexR = getIndexRInTask(remote_task, resource);
                        int number_of_release = (int) Math
                                .ceil((double) (Ri + (btbHit ? (useRi ? remote_task.Ri_LO : remote_task.deadline) : 0)) / (double) remote_task.period);
                        number_of_request_by_RemoteLowTask_P += number_of_release * remote_task.number_of_access_in_one_release.get(indexR);
                    }
                }
                // remote high task blocking
                possible_spin_delay = number_of_request_by_RemoteLowTask_P - getNoRFromHP < 0 ? 0 : number_of_request_by_RemoteLowTask_P - getNoRFromHP;


                number_of_spin_dealy += Integer.min(possible_spin_delay, NoRFromT) * resource.csl_low;
                number_of_spin_low += Integer.min(possible_spin_delay, NoRFromT);

            }
        }
        ArrayList<Integer> number_of_spin = new ArrayList<>();
        number_of_spin.add(number_of_spin_high);
        number_of_spin.add(number_of_spin_low);
        return number_of_spin;
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

    /*
     * gives that number of requests from HP local tasks for a resource that is
     * required by the given task.
     */
    private int getNoRFromHP(Resource resource, SporadicTask task, ArrayList<SporadicTask> tasks, long[] Ris, long Ri, boolean btbHit, boolean useRi, boolean useRiLow) {
        int number_of_request_by_HP = 0;
        int priority = task.priority;

        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).priority > priority && tasks.get(i).resource_required_index.contains(resource.id - 1)) {
                SporadicTask hpTask = tasks.get(i);
                int indexR = getIndexRInTask(hpTask, resource);
                if (useRiLow)
                    number_of_request_by_HP += Math.ceil((double) (Ri + (btbHit ? (useRi ? hpTask.Ri_LO : hpTask.deadline) : 0)) / (double) hpTask.period)
                            * hpTask.number_of_access_in_one_release.get(indexR);
                else
                    number_of_request_by_HP += Math.ceil((double) (Ri + (btbHit ? (useRi ? Ris[i] : hpTask.deadline) : 0)) / (double) hpTask.period)
                            * hpTask.number_of_access_in_one_release.get(indexR);
            }
        }
        return number_of_request_by_HP;
    }

    /**
     * FIFO-P resource accessing time.
     */
    private long FIFOPResourceAccessTime(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> lowTasks,
                                         long[][] Ris, long time,
                                         boolean btbHit, boolean useRi) {
        long spin = 0;
        //ArrayList<ArrayList<Long>> requestsLeftOnRemoteP = new ArrayList<>();
        //RBTQ
        ArrayList<ArrayList<ArrayList<Long>>> requestsLeftOnRemoteP = new ArrayList<>();
        ArrayList<Resource> fifo_resources = new ArrayList<>();
        for (int i = 0; i < resources.size(); i++) {
            Resource res = resources.get(i);
            // 控制FIFO-P相关的protocol
            // ArrayList<ArrayList<Long>> requestsLeftOnRemoteP
            if (res.protocol >= 2 && res.protocol <= 5) {
                requestsLeftOnRemoteP.add(new ArrayList<ArrayList<Long>>());
                fifo_resources.add(res);
                spin += getSpinDelayForOneResoruce(task, tasks, res, lowTasks, requestsLeftOnRemoteP.get(requestsLeftOnRemoteP.size() - 1), Ris, time, btbHit, useRi);
            } else {
                requestsLeftOnRemoteP.add(new ArrayList<ArrayList<Long>>());
            }
        }

        if (fifo_resources.size() > 0) {

            // preemptions
            long preemptions = 0;
            long sum_preemptions = 0;
            long request_by_preemptions = 0;
            // 建立priority-preemptions对儿将其保存下来便于后续使用
            Map<Integer, Long> pp = new HashMap<Integer, Long>();
            for (int i = 0; i < tasks.get(task.partition).size(); i++) {
                int high_task_priority = tasks.get(task.partition).get(i).priority;
                if (high_task_priority > task.priority) {
                    // 在这个高优先级任务的'优先级'下，抢占次数
                    preemptions = (int) Math.ceil((time) / (double) tasks.get(task.partition).get(i).period);
                    sum_preemptions += preemptions;
                    pp.put(high_task_priority, preemptions);
                }
            }
            for (int i = 0; i < lowTasks.get(task.partition).size(); i++) {
                int high_task_priority = lowTasks.get(task.partition).get(i).priority;
                if (high_task_priority > task.priority) {
                    // 在这个高优先级任务的'优先级'下，抢占次数
                    preemptions = (int) Math.ceil((time) / (double) lowTasks.get(task.partition).get(i).period);
                    sum_preemptions += preemptions;
                    pp.put(high_task_priority, preemptions);
                }
            }

            task.implementation_overheads += sum_preemptions * (AnalysisUtils.FIFOP_CANCEL);
            task.blocking_overheads += sum_preemptions * (AnalysisUtils.FIFOP_CANCEL);
            //key 降序
            Map<Integer, Long> sortedMap = pp.entrySet()
                    .stream()
                    .sorted(Map.Entry.<Integer, Long>comparingByKey().reversed())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new
                    ));

            // 新算法开始
            // 1. 需要计算访问各requestsLeftOnRemoteP的csl-Queue；
            ArrayList<ArrayList<Long>> all_csl_queue = new ArrayList<>();
            //requestsLeftOnRemoteP.size() == number of resource
            for (int p = 0; p < requestsLeftOnRemoteP.size(); p++) {
                // one resource blocking time
                ArrayList<ArrayList<Long>> rlorp = new ArrayList<ArrayList<Long>>(requestsLeftOnRemoteP.get(p));
                ArrayList<Long> csl_queue = new ArrayList<>();
                while (rlorp.size() != 0) {

                    long csl_sum = 0;
                    for (int z = 0; z < rlorp.size(); z++) {
                        // 第z个远程处理器的RBTQ第一项
                        csl_sum += rlorp.get(z).remove(0);
                        if (rlorp.get(z).size() == 0)
                            rlorp.remove(z);
                        z--;
                    }

                    csl_queue.add(csl_sum);

                    /*for(int q = 0; q < rlorp.size(); q++){
                        rlorp.set(q, rlorp.get(q) - 1);
                        if (rlorp.get(q) < 1) {
                            rlorp.remove(q);
                            q--;
                        }
                    }*/
                }
                all_csl_queue.add(csl_queue);
            }

            Map<Set<Integer>, Long> www = new HashMap<Set<Integer>, Long>();
            for (Integer high_task_priority : sortedMap.keySet()) {
                long preempt = sortedMap.get(high_task_priority);

                Set<Integer> rIndexList = new HashSet<>();
                for (int w = 0; w < task.resource_required_index.size(); w++) {
                    int rIndex = task.resource_required_index.get(w);
                    if (high_task_priority > task.resource_required_priority.get(w)) {
                        if (resources.get(rIndex).protocol >= 2 && resources.get(rIndex).protocol <= 5) {
                            rIndexList.add(rIndex);
                        }
                    }
                }
                // 任务\tau_x的高优先级任务
                ArrayList<SporadicTask> taskset = tasks.get(task.partition);
                taskset.addAll(lowTasks.get(task.partition));   // lowTask
                for (int c = 0; c < taskset.size(); c++) {
                    SporadicTask high_task = taskset.get(c);
                    if (high_task.priority > task.priority) {
                        for (int w = 0; w < high_task.resource_required_index.size(); w++) {
                            int rIndex = high_task.resource_required_index.get(w);
                            if (high_task_priority > high_task.resource_required_priority.get(w)) {
                                if (resources.get(rIndex).protocol >= 2 && resources.get(rIndex).protocol <= 5) {
                                    rIndexList.add(rIndex);
                                }
                            }
                        }
                    }
                }
                if (rIndexList.size() != 0) {
                    www.put(rIndexList, www.getOrDefault(rIndexList, (long) 0) + preempt);
                }
            }
            Map<Set<Integer>, Long> sortedwww = www.entrySet()
                    .stream()
                    .sorted(Map.Entry.<Set<Integer>, Long>comparingByKey(Comparator.comparingInt(set -> set.size())).reversed())
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            Map.Entry::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new));
            ArrayList<Long> sum_array = new ArrayList<>();
            if (sortedwww.size() > 0) {
                List<Map.Entry<Set<Integer>, Long>> entryList = new ArrayList<>(sortedwww.entrySet());
                Long sum_preempt = 0L;
                // 迭代过程优化
                for (int i = 0; i < entryList.size(); i++) {
                    Map.Entry<Set<Integer>, Long> A = entryList.get(i);
                    Map.Entry<Set<Integer>, Long> B = i == entryList.size() - 1 ? null : entryList.get(i + 1);
                    Set<Integer> A_list = A.getKey();
                    Set<Integer> B_list = B == null ? new HashSet<>() : B.getKey();
                    Long A_preempt = A.getValue();
                    sum_preempt += A_preempt;
                    // 创建一个新的集合来存储差集，以便保持原始集合不变
                    Set<Integer> differenceSet = new HashSet<>(A_list);
                    // 从differenceSet中移除setA中的所有元素，得到差集
                    differenceSet.removeAll(B_list);
                    // 从differnceSet中索引指示的all_csl_queue中的几个queue合并，并取前sum_preempt个最大的数存入sum_array中
                    ArrayList<Long> sum_list = new ArrayList<>();
                    for (Integer index : differenceSet) {
                        sum_list.addAll(all_csl_queue.get(index));
                    }
                    // sum_array和sum_list合并
                    sum_array.addAll(sum_list);
                    Collections.sort(sum_array, (o1, o2) -> o2.compareTo(o1));
                    ArrayList<Long> subList = new ArrayList<>(sum_array.subList(0, (int) Math.min(sum_array.size(), sum_preempt)));
                    sum_array = subList;
                }
            }
            for (Long num : sum_array) {
                spin += num;
            }

            task.spin_delay_by_preemptions = request_by_preemptions;

        }
        return spin;
    }

    // 返回包含2-5protocol的间接spin等
    private long getSpinDelayForOneResoruce(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, Resource resource, ArrayList<ArrayList<SporadicTask>> LowTasks,
                                            ArrayList<ArrayList<Long>> requestsLeftOnRemoteP, long[][] Ris, long time, boolean btbHit, boolean useRi) {
        long spin = 0;
        long nspin = 0;
        long ncs = 0;
        long ncs_lo = 0;
        long ncs_hi = 0;
        long N_i_k = 0;

        // 本地高优先级任务访问资源的次数，LowTask部分
        for (int i = 0; i < LowTasks.get(task.partition).size(); i++) {
            SporadicTask hpTask = LowTasks.get(task.partition).get(i);
            if (hpTask.priority > task.priority && hpTask.resource_required_index.contains(resource.id - 1)) {
                int n_j_k = hpTask.number_of_access_in_one_release.get(hpTask.resource_required_index.indexOf(resource.id - 1));
                ncs_lo += (long) Math.ceil((double) (task.Ri_LO + (btbHit ? (useRi ? hpTask.Ri_LO : hpTask.deadline) : 0)) / (double) hpTask.period) * n_j_k;
            }
        }
        // 本地高优先级任务访问资源的次数，HiTask部分
        for (int i = 0; i < tasks.get(task.partition).size(); i++) {
            SporadicTask hpTask = tasks.get(task.partition).get(i);
            if (hpTask.priority > task.priority && hpTask.resource_required_index.contains(resource.id - 1)) {
                ncs_hi += (long) Math.ceil((double) (time + (btbHit ? (useRi ? Ris[task.partition][i] : hpTask.deadline) : 0)) / (double) hpTask.period)
                        * hpTask.number_of_access_in_one_release.get(hpTask.resource_required_index.indexOf(resource.id - 1));
            }
        }
        // 任务自身访问资源的次数
        if (task.resource_required_index.contains(resource.id - 1))
            N_i_k += task.number_of_access_in_one_release.get(task.resource_required_index.indexOf(resource.id - 1));

        ncs = ncs_hi + ncs_lo + N_i_k;

        // remote task
        if (ncs > 0) {
            for (int i = 0; i < tasks.size(); i++) {
                if (task.partition != i) {
                    /* For each remote partition */
                    long number_of_low_request_by_Remote_P = 0;
                    long number_of_high_request_by_Remote_P = 0;
                    // 远程处理器HI任务访问资源次数
                    for (int j = 0; j < tasks.get(i).size(); j++) {
                        if (tasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
                            SporadicTask remote_task = tasks.get(i).get(j);
                            int indexR = getIndexRInTask(remote_task, resource);
                            int number_of_release = (int) Math
                                    .ceil((double) (time + (btbHit ? (useRi ? Ris[i][j] : remote_task.deadline) : 0)) / (double) remote_task.period);
                            number_of_high_request_by_Remote_P += (long) number_of_release * remote_task.number_of_access_in_one_release.get(indexR);
                        }
                    }
                    // 远程处理器LO任务访问资源次数
                    for (int j = 0; j < LowTasks.get(i).size(); j++) {
                        if (LowTasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
                            SporadicTask remote_task = LowTasks.get(i).get(j);
                            int indexR = getIndexRInTask(remote_task, resource);
                            long number_of_release_lo = (long) Math.ceil((double) (task.Ri_LO + (btbHit ? (useRi ? remote_task.Ri_LO : remote_task.deadline) : 0)) / (double) remote_task.period);
                            number_of_low_request_by_Remote_P += number_of_release_lo * remote_task.number_of_access_in_one_release.get(indexR);
                        }
                    }

                    long possible_spin_delay = Long.min(number_of_high_request_by_Remote_P + number_of_low_request_by_Remote_P, ncs);
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
                    if (RBTQ.size() > 0)
                        requestsLeftOnRemoteP.add(RBTQ);

                    nspin += possible_spin_delay;
                    // long possible_spin_delay = Long.min(number_of_request_by_Remote_P, ncs);
                    // spin += possible_spin_delay;
                    // if (number_of_request_by_Remote_P - ncs > 0)
                    //    requestsLeftOnRemoteP.add(number_of_request_by_Remote_P - ncs);
                }
            }
        }

        task.implementation_overheads += (nspin + ncs) * (AnalysisUtils.FIFOP_LOCK + AnalysisUtils.FIFOP_UNLOCK);
        task.blocking_overheads += (nspin + ncs
                - (task.resource_required_index.contains(resource.id - 1)
                ? task.number_of_access_in_one_release.get(task.resource_required_index.indexOf(resource.id - 1))
                : 0))
                * (AnalysisUtils.FIFOP_LOCK + AnalysisUtils.FIFOP_UNLOCK);
        return spin + ncs_lo * resource.csl_low + ncs_hi * resource.csl_high + N_i_k * resource.csl_high;
        //return spin * resource.csl + ncs * resource.csl;
    }

    /**
     * MrsP resource accessing time.
     */
    private long MrsPresourceAccessingTime(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> LowTasks,
                                           long[][] Ris, long time,
                                           long jitter, double oneMig, long np, boolean btbHit, boolean useRi, SporadicTask calT) {
        long resource_accessing_time = 0;

        for (int i = 0; i < task.resource_required_index.size(); i++) {
            Resource resource = resources.get(task.resource_required_index.get(i));

            if (resource.protocol == 6 || resource.protocol == 7) {
                // 这里的jitter为0   任务请求？？
                int number_of_request_with_btb = (int) Math.ceil((double) (time + jitter) / (double) task.period) * task.number_of_access_in_one_release.get(i);

                for (int j = 1; j < number_of_request_with_btb + 1; j++) {
                    long oneAccess = 0;
                    oneAccess += MrsPresourceAccessingTimeInOne(task, resource, tasks, LowTasks, Ris, time, jitter, j, btbHit, useRi, calT);

                    if (oneMig != 0) {
                        double mc = migrationCostForSpin(task, resource, tasks, LowTasks, Ris, time, j, oneMig, np, btbHit, useRi, calT);
                        long mc_long = (long) Math.floor(mc);
                        calT.migration_overheads_plus += mc - mc_long;
                        if (mc - mc_long < 0) {
                            System.err.println("MrsP mig error");
                            System.exit(-1);
                        }
                        oneAccess += mc_long;
                    }

                    resource_accessing_time += oneAccess;
                }
            }

        }

        return resource_accessing_time;
    }

    private long MrsPresourceAccessingTimeInOne(SporadicTask task, Resource resource, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks,
                                                long[][] Ris, long time,
                                                long jitter, int request_index, boolean btbHit, boolean useRi, SporadicTask calTask) {
        int number_of_access = 0;
        long spin = 0;

        // 本地高优先级任务请求数量
        int getNoRFromHPHigh = getNoRFromHP(resource, task, tasks.get(task.partition), Ris[task.partition], time, btbHit, useRi, false);
        int getNoRFromHPLow = getNoRFromHP(resource, task, LowTasks.get(task.partition), Ris[task.partition], time, btbHit, useRi, true);

        // 高优先级 + 本次请求序号
        int NoR = getNoRFromHPHigh + getNoRFromHPLow + request_index;

        // 遍历远程处理器
        for (int i = 0; i < tasks.size(); i++) {
            if (i != task.partition) {
                /* For each remote partition */
                int number_of_high_request_by_Remote_P = 0;
                int number_of_low_request_by_Remote_P = 0;

                for (int j = 0; j < tasks.get(i).size(); j++) {
                    if (tasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
                        SporadicTask remote_task = tasks.get(i).get(j);
                        int indexR = getIndexRInTask(remote_task, resource);
                        int number_of_release = (int) Math
                                .ceil((double) (time + (btbHit ? (useRi ? Ris[i][j] : remote_task.deadline) : 0)) / (double) remote_task.period);
                        number_of_high_request_by_Remote_P += number_of_release * remote_task.number_of_access_in_one_release.get(indexR);
                    }
                }
                for (int j = 0; j < LowTasks.get(i).size(); j++) {
                    if (LowTasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
                        SporadicTask remote_task = LowTasks.get(i).get(j);
                        int indexR = getIndexRInTask(remote_task, resource);
                        int number_of_release = (int) Math
                                .ceil((double) (time + (btbHit ? (useRi ? task.Ri_LO : remote_task.deadline) : 0)) / (double) remote_task.period);
                        number_of_low_request_by_Remote_P += number_of_release * remote_task.number_of_access_in_one_release.get(indexR);
                    }
                }
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

                // 取队列中相应位置的csl
                spin += NoR > 0 ? (NoR <= RBTQ.size() ? RBTQ.get(NoR - 1) : 0) : 0;

                int possible_spin_delay = Math.max((number_of_high_request_by_Remote_P + number_of_low_request_by_Remote_P) - (getNoRFromHPHigh + getNoRFromHPLow) - request_index + 1, 0);
                number_of_access += Integer.min(possible_spin_delay, 1);
            }
        }

        // account for the request of the task itself
        number_of_access++;
        spin += resource.csl_high;

        calTask.implementation_overheads += number_of_access * (AnalysisUtils.MrsP_LOCK + AnalysisUtils.MrsP_UNLOCK);
        calTask.blocking_overheads += (number_of_access - 1) * (AnalysisUtils.MrsP_LOCK + AnalysisUtils.MrsP_UNLOCK);

        return spin;
        //return number_of_access * resource.csl;
    }

    /***************************************************
     ************* Migration Cost Calculation **********
     ***************************************************/
    private double migrationCostForSpin(SporadicTask task, Resource resource, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks,
                                        long[][] Ris, long time,
                                        int request_number, double oneMig, long np, boolean btbHit, boolean useRi, SporadicTask calT) {

        ArrayList<Integer> migration_targets = new ArrayList<>();

        // identify the migration targets
        migration_targets.add(task.partition);
        for (int i = 0; i < tasks.size(); i++) {
            if (i != task.partition) {
                int number_requests_left = 0;
                number_requests_left = getNoRRemote(resource, tasks.get(i), LowTasks.get(i), Ris[i], time, btbHit, useRi)
                        - getNoRFromHP(resource, task, tasks.get(task.partition), Ris[task.partition], time, btbHit, useRi, false)
                        - getNoRFromHP(resource, task, tasks.get(task.partition), Ris[task.partition], time, btbHit, useRi, true)
                        - request_number + 1;

                if (number_requests_left > 0)
                    migration_targets.add(i);
            }
        }
        double duration = calT.critical == 0? resource.csl_low : resource.csl_high;
        return migrationCost(calT, resource, tasks, LowTasks, migration_targets, oneMig, np, duration);
    }


    // return the totol number of request on remote processor
    private int getNoRRemote(Resource resource, ArrayList<SporadicTask> tasks, ArrayList<SporadicTask> LowTasks,
                             long[] Ris, long Ri, boolean btbHit, boolean useRi) {
        int number_of_request_by_Remote_P = 0;

        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).resource_required_index.contains(resource.id - 1)) {
                SporadicTask remote_task = tasks.get(i);
                int indexR = getIndexRInTask(remote_task, resource);
                number_of_request_by_Remote_P += Math.ceil((double) (Ri + (btbHit ? (useRi ? Ris[i] : remote_task.deadline) : 0)) / (double) remote_task.period)
                        * remote_task.number_of_access_in_one_release.get(indexR);
            }
        }
        for (int i = 0; i < LowTasks.size(); i++) {
            if (LowTasks.get(i).resource_required_index.contains(resource.id - 1)) {
                SporadicTask remote_task = LowTasks.get(i);
                int indexR = getIndexRInTask(remote_task, resource);
                number_of_request_by_Remote_P += Math.ceil((double) (Ri + (btbHit ? (useRi ? remote_task.Ri_LO : remote_task.deadline) : 0)) / (double) remote_task.period)
                        * remote_task.number_of_access_in_one_release.get(indexR);
            }
        }
        return number_of_request_by_Remote_P;
    }

    private double migrationCost(SporadicTask calT, Resource resource, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks,
                                 ArrayList<Integer> migration_targets,
                                 double oneMig, long np, double duration) {
        double migrationCost = 0;
        ArrayList<Integer> migration_targets_with_P = new ArrayList<>();

        // identify the migration targets with preemptors
        for (int i = 0; i < migration_targets.size(); i++) {
            int partition = migration_targets.get(i);

            if (resource.protocol == 6) {
                int ceiling = Integer.max(resource.getCeilingForProcessor(tasks, partition), resource.getCeilingForProcessor(LowTasks, partition));

                if (tasks.get(partition).get(0).priority > ceiling || LowTasks.get(partition).get(0).priority > ceiling)   // 保证优先级降序
                    migration_targets_with_P.add(migration_targets.get(i));

            } else if (resource.protocol == 7) {
                int max_pri = getMaxPriorityOfPartition(tasks, partition);
                max_pri = Integer.max(max_pri, getMaxPriorityOfPartition(LowTasks, partition));
                int ceiling_pri = Integer.max(resource.getCeilingForProcessor(tasks, partition), resource.getCeilingForProcessor(LowTasks, partition));

                int compare_pri = (int) Math.ceil(ceiling_pri + ((double) (max_pri - ceiling_pri) / 2.0));

                if (tasks.get(partition).get(0).priority > compare_pri)
                    migration_targets_with_P.add(migration_targets.get(i));
            }

        }

        // check
        if (!migration_targets.containsAll(migration_targets_with_P)) {
            System.out.println("migration targets error!");
            System.exit(0);
        }

        // now we compute the migration cost for each request
        for (int i = 0; i < migration_targets.size(); i++) {
            double migration_cost_for_one_access = 0;
            int partition = migration_targets.get(i); // the request issued
            // from.

            // calculating migration cost
            // 1. If there is no preemptors on the task's partition OR there is
            // no
            // other migration targets
            if (!migration_targets_with_P.contains(partition) || (migration_targets.size() == 1 && migration_targets.get(0) == partition))
                migration_cost_for_one_access = 0;

                // 2. If there is preemptors on the task's partition AND there are
                // no
                // preemptors on other migration targets
            else if (migration_targets_with_P.size() == 1 && migration_targets_with_P.get(0) == partition && migration_targets.size() > 1)
                migration_cost_for_one_access = 2 * oneMig;

                // 3. If there exist multiple migration targets with preemptors.
                // With NP
                // section applied.
            else {
                if (np > 0) {
                    double migCostWithNP = (long) (1 + Math.ceil( duration / (double) np)) * oneMig;
                    double migCostWithHP = migrationCostBusyWindow(migration_targets_with_P, oneMig, resource, tasks, calT, migCostWithNP, duration);
                    migration_cost_for_one_access = Math.min(migCostWithHP, migCostWithNP);
                } else {
                    migration_cost_for_one_access = migrationCostBusyWindow(migration_targets_with_P, oneMig, resource, tasks, calT, -1, duration);
                }
            }

            migrationCost += migration_cost_for_one_access;
        }

        return migrationCost;
    }

    private int getMaxPriorityOfPartition(ArrayList<ArrayList<SporadicTask>> tasks, int partition) {
        ArrayList<SporadicTask> taskset = tasks.get(partition);
        int max_priority = -1;
        for (SporadicTask t : taskset) {
            if (t.priority > max_priority) {
                max_priority = t.priority;
            }
        }
        return max_priority;


    }

    private double migrationCostBusyWindow(ArrayList<Integer> migration_targets_with_P, double oneMig, Resource resource,
                                           ArrayList<ArrayList<SporadicTask>> tasks, SporadicTask calT, double migByNP, double duration) {
        double migCost = 0;


        double newMigCost = migrationCostOneCal(migration_targets_with_P, oneMig, duration + migCost, resource, tasks);

        while (migCost != newMigCost) {
            migCost = newMigCost;
            newMigCost = migrationCostOneCal(migration_targets_with_P, oneMig, duration + migCost, resource, tasks);

            if (newMigCost > calT.deadline) {
                return newMigCost;
            }
            if (migByNP > 0 && newMigCost > migByNP) {
                return newMigCost;
            }
        }

        return migCost;
    }

    private double migrationCostOneCal(ArrayList<Integer> migration_targets_with_P, double oneMig, double duration, Resource resource,
                                       ArrayList<ArrayList<SporadicTask>> tasks) {
        double migCost = 0;

        for (int i = 0; i < migration_targets_with_P.size(); i++) {
            int partition_with_p = migration_targets_with_P.get(i);

            for (int j = 0; j < tasks.get(partition_with_p).size(); j++) {
                SporadicTask hpTask = tasks.get(partition_with_p).get(j);

                // 条件修改 + low task

                if(resource.protocol == 6){
                    if (hpTask.priority > resource.getCeilingForProcessor(tasks, partition_with_p))
                        migCost += Math.ceil((duration) / hpTask.period) * oneMig;
                } else if (resource.protocol == 7) {
                    int max_pri = getMaxPriorityOfPartition(tasks,partition_with_p);
                    int ceiling_pri = resource.getCeilingForProcessor(tasks, partition_with_p);
                    int compare_pri = (int) Math.ceil(ceiling_pri + ((double)(max_pri - ceiling_pri) / 2.0));
                    if (hpTask.priority > compare_pri)
                        migCost += Math.ceil((duration) / hpTask.period) * oneMig;
                }

//
//				if (hpTask.priority > resource.getCeilingForProcessor(tasks, partition_with_p))
//					migCost += Math.ceil((duration) / hpTask.period) * oneMig;
            }
        }

        return migCost + oneMig;
    }

    /******************************************************
     ************* Migration Cost Calculation END *********
     ******************************************************/



    /***************************************************
     ************* InDirect Spin Delay *******************
     ***************************************************/
    private long highPriorityInterference(SporadicTask t, ArrayList<ArrayList<SporadicTask>> allTasks, ArrayList<Resource> resources,
                                          ArrayList<ArrayList<SporadicTask>> LowTasks,
                                          long[][] Ris, long time,
                                          double oneMig, long np, boolean btbHit, boolean useRi) {
        long interference = 0;
        int partition = t.partition;
        // 高关键任务
        ArrayList<SporadicTask> tasks = allTasks.get(partition);

        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).priority > t.priority) {
                SporadicTask hpTask = tasks.get(i);
                interference += Math.ceil((double) (time) / (double) hpTask.period) * (hpTask.WCET);
                t.implementation_overheads += Math.ceil((double) (time) / (double) hpTask.period) * (AnalysisUtils.FULL_CONTEXT_SWTICH2);
                // 间接阻塞
                long btb_interference = getIndirectSpinDelay(hpTask, allTasks, resources, LowTasks, Ris, time, Ris[partition][i], btbHit, useRi, t);
                // 迁移成本
                interference += MrsPresourceAccessingTime(hpTask, allTasks, resources, LowTasks, Ris, time, btbHit ? (useRi ? Ris[partition][i] : hpTask.deadline) : 0,
                        oneMig, np, btbHit, useRi, t);
                t.indirect_spin += btb_interference;
                interference += btb_interference;
            }
        }
        // 低关键任务
        ArrayList<SporadicTask> lowTasks = LowTasks.get(partition);

        for (int i = 0; i < lowTasks.size(); i++) {
            if (lowTasks.get(i).priority > t.priority) {
                SporadicTask hpTask = lowTasks.get(i);
                interference += Math.ceil((double) (time) / (double) hpTask.period) * (hpTask.WCET);
                t.implementation_overheads += Math.ceil((double) (time) / (double) hpTask.period) * (AnalysisUtils.FULL_CONTEXT_SWTICH2);

                long btb_interference = getIndirectSpinDelay(hpTask, allTasks, resources, LowTasks, Ris, time, Ris[partition][i], btbHit, useRi, t);
                interference += MrsPresourceAccessingTime(hpTask, allTasks, resources, LowTasks, Ris, time, btbHit ? (useRi ? Ris[partition][i] : hpTask.deadline) : 0,
                        oneMig, np, btbHit, useRi, t);
                t.indirect_spin += btb_interference;
                interference += btb_interference;
            }
        }


        return interference;
    }

    /**
     * FIFO-NP indirect spin delay.
     */
    private long getIndirectSpinDelay(SporadicTask hpTask, ArrayList<ArrayList<SporadicTask>> allTasks, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> LowTasks,
                                      long[][] Ris, long Ri,
                                      long Rihp, boolean btbHit, boolean useRi, SporadicTask calTask) {
        long BTBhit = 0;

        for (int i = 0; i < hpTask.resource_required_index.size(); i++) {
            /* for each resource that a high priority task request */
            Resource resource = resources.get(hpTask.resource_required_index.get(i));

//			if (resource.protocol != 2 && resource.protocol != 3) {
            if (resource.protocol == 1) {
                // 比hpTask高优先级任务request次数
                int number_of_higher_request = getNoRFromHP(resource, hpTask, allTasks.get(hpTask.partition), Ris[hpTask.partition], Ri, btbHit, useRi, false)+
                        getNoRFromHP(resource, hpTask, LowTasks.get(hpTask.partition), Ris[hpTask.partition], Ri, btbHit, useRi, false);
                // hpTask request次数
                int number_of_request_with_btb = (int) Math.ceil((double) (Ri + (btbHit ? (useRi ? Rihp : hpTask.deadline) : 0)) / (double) hpTask.period)
                        * hpTask.number_of_access_in_one_release.get(i);

                BTBhit += number_of_request_with_btb * resource.csl_high;
                calTask.implementation_overheads += number_of_request_with_btb * (AnalysisUtils.FIFONP_LOCK + AnalysisUtils.FIFONP_UNLOCK);
                calTask.blocking_overheads += number_of_request_with_btb * (AnalysisUtils.FIFONP_LOCK + AnalysisUtils.FIFONP_UNLOCK);

                for (int j = 0; j < resource.partitions.size(); j++) {
                    if (resource.partitions.get(j) != hpTask.partition) {
                        int remote_partition = resource.partitions.get(j);
                        int number_of_remote_request = getNoRRemote(resource, allTasks.get(remote_partition), null, Ris[remote_partition], Ri, btbHit, useRi);
                        int number_of_remote_request_from_low = getNoRRemote(resource,null,  LowTasks.get(remote_partition), Ris[remote_partition], Ri, btbHit, useRi);

                        // RBTQ
                        ArrayList<Long> RBTQ = new ArrayList<>();
                        while (number_of_remote_request > 0) {
                            RBTQ.add(resource.csl_high);
                            number_of_remote_request--;
                        }
                        //min{local, remote m}
                        while (number_of_remote_request_from_low > 0) {
                            RBTQ.add(resource.csl_low);
                            number_of_remote_request_from_low--;
                        }

                        int possible_spin_delay = Math.max(number_of_remote_request + number_of_remote_request_from_low - number_of_higher_request, 0);
                        int spin_delay_with_btb = Integer.min(possible_spin_delay, number_of_request_with_btb);

                        int k=number_of_higher_request;

                        while(k<number_of_higher_request+number_of_request_with_btb && k<RBTQ.size()){
                            BTBhit += RBTQ.get(k);
                            k++;
                        }

                        //BTBhit += spin_delay_with_btb * resource.csl;
                        calTask.implementation_overheads += spin_delay_with_btb * (AnalysisUtils.FIFONP_LOCK + AnalysisUtils.FIFONP_UNLOCK);
                        calTask.blocking_overheads += spin_delay_with_btb * (AnalysisUtils.FIFONP_LOCK + AnalysisUtils.FIFONP_UNLOCK);
                    }
                }
            }

        }
        return BTBhit;
    }

    /***************************************************
     ************** Arrival Blocking *******************
     ***************************************************/
    private long localBlocking(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> LowTasks,
                               long[][] Ris, long time, double oneMig,
                               long np, boolean btbHit, boolean useRi) {
        long localblocking = 0;
        long fifonp_localblocking = FIFONPlocalBlocking(t, tasks, resources, LowTasks, Ris, time, btbHit, useRi);
        long fifop_localblocking = FIFOPlocalBlocking(t, tasks, resources, LowTasks, Ris, time, btbHit, useRi);
        long MrsP_localblocking = MrsPlocalBlocking(t, tasks, resources, LowTasks, Ris, time, oneMig, np, btbHit, useRi);
        ArrayList<SporadicTask> localTasks = tasks.get(t.partition);   localTasks.addAll(LowTasks.get(t.partition));
        long npsection = (isTaskIncurNPSection(t, localTasks, resources) ? np : 0);

        ArrayList<Double> blocking = new ArrayList<>();

        double fifonp = t.fifonp_arrivalblocking_overheads + fifonp_localblocking;
        double fifop = t.fifop_arrivalblocking_overheads + fifop_localblocking;
        double mrsp = t.mrsp_arrivalblocking_overheads + MrsP_localblocking;

        blocking.add(fifonp);
        blocking.add(fifop);
        blocking.add(mrsp);
        blocking.add((double) npsection);

        blocking.sort((l1, l2) -> -Double.compare(l1, l2));

        blocking.sort((l1, l2) -> -Double.compare(l1, l2));

        if (blocking.get(0) == fifonp) {
            t.np_section = 0;
            localblocking = fifonp_localblocking;
            t.implementation_overheads += t.fifonp_arrivalblocking_overheads;
            t.blocking_overheads += t.fifonp_arrivalblocking_overheads;
        } else if (blocking.get(0) == fifop) {
            t.np_section = 0;
            localblocking = fifop_localblocking;
            t.implementation_overheads += t.fifop_arrivalblocking_overheads;
            t.blocking_overheads += t.fifonp_arrivalblocking_overheads;
        } else if (blocking.get(0) == mrsp) {
            t.np_section = 0;
            localblocking = MrsP_localblocking;
            t.implementation_overheads += t.mrsp_arrivalblocking_overheads;
            t.blocking_overheads += t.fifonp_arrivalblocking_overheads;
        } else if (blocking.get(0) == npsection) {
            t.np_section = npsection;
            localblocking = npsection;
        }

        return localblocking;
    }

    private long FIFONPlocalBlocking(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> LowTasks,
                                     long[][] Ris, long Ri,
                                     boolean btbHit, boolean useRi) {
        ArrayList<SporadicTask> localTask = tasks.get(t.partition); localTask.addAll(LowTasks.get(t.partition));
        ArrayList<Resource> LocalBlockingResources_HI = FIFONPgetLocalBlockingResources_HI(t, resources, localTask);
        ArrayList<Resource> LocalBlockingResources_LO = FIFONPgetLocalBlockingResources_LO(t, resources, localTask);

        ArrayList<Long> local_blocking_each_resource = new ArrayList<>();
        ArrayList<Double> overheads = new ArrayList<>();

        for (int i = 0; i < LocalBlockingResources_HI.size(); i++) {
            Resource res = LocalBlockingResources_HI.get(i);
            long local_blocking = res.csl_high;
            long number_of_blocking = 1;

            if (res.isGlobal) {
                for (int parition_index = 0; parition_index < res.partitions.size(); parition_index++) {
                    int partition = res.partitions.get(parition_index);
                    int norHP = getNoRFromHP(res, t, tasks.get(t.partition), Ris[t.partition], Ri, btbHit, useRi, false)+
                            getNoRFromHP(res, t, LowTasks.get(t.partition), Ris[t.partition], Ri, btbHit, useRi, true);
                    int norT = t.resource_required_index.contains(res.id - 1)
                            ? t.number_of_access_in_one_release.get(t.resource_required_index.indexOf(res.id - 1))
                            : 0;
                    int norR_high = getNoRRemote(res, tasks.get(partition), null, Ris[partition], Ri, btbHit, useRi);
                    int norR_low = getNoRRemote(res, null, LowTasks.get(partition), Ris[partition], Ri, btbHit, useRi);

                    if (partition != t.partition) {
                        if ( (norHP + norT) < norR_high){
                            local_blocking += res.csl_high;
                            number_of_blocking++;
                        }
                        else if ( (norHP + norT) < norR_high+norR_low){
                            local_blocking += res.csl_low;
                            number_of_blocking++;
                        }

                    }
                }
            }
            local_blocking_each_resource.add(local_blocking);
            overheads.add(number_of_blocking * (AnalysisUtils.FIFONP_LOCK + AnalysisUtils.FIFONP_UNLOCK));
            //overheads.add((local_blocking / res.csl) * (AnalysisUtils.FIFONP_LOCK + AnalysisUtils.FIFONP_UNLOCK));
        }
        for (int i = 0; i < LocalBlockingResources_LO.size(); i++) {
            Resource res = LocalBlockingResources_LO.get(i);
            long local_blocking = res.csl_low;
            long number_of_blocking = 1;

            if (res.isGlobal) {
                for (int parition_index = 0; parition_index < res.partitions.size(); parition_index++) {
                    int partition = res.partitions.get(parition_index);
                    int norHP = getNoRFromHP(res, t, tasks.get(t.partition), Ris[t.partition], Ri, btbHit, useRi, false)+
                            getNoRFromHP(res, t, LowTasks.get(t.partition), Ris[t.partition], Ri, btbHit, useRi, true);
                    int norT = t.resource_required_index.contains(res.id - 1)
                            ? t.number_of_access_in_one_release.get(t.resource_required_index.indexOf(res.id - 1))
                            : 0;
                    int norR_high = getNoRRemote(res, tasks.get(partition), null, Ris[partition], Ri, btbHit, useRi);
                    int norR_low = getNoRRemote(res, null, LowTasks.get(partition), Ris[partition], Ri, btbHit, useRi);

                    if (partition != t.partition) {
                        if ( (norHP + norT) < norR_high){
                            local_blocking += res.csl_high;
                            number_of_blocking++;
                        }
                        else if ( (norHP + norT) < norR_high+norR_low){
                            local_blocking += res.csl_low;
                            number_of_blocking++;
                        }

                    }
                }
            }
            local_blocking_each_resource.add(local_blocking);
            overheads.add(number_of_blocking * (AnalysisUtils.FIFONP_LOCK + AnalysisUtils.FIFONP_UNLOCK));
            //overheads.add((local_blocking / res.csl) * (AnalysisUtils.FIFONP_LOCK + AnalysisUtils.FIFONP_UNLOCK));
        }

        if (local_blocking_each_resource.size() >= 1) {
            local_blocking_each_resource.sort((l1, l2) -> -Double.compare(l1, l2));
            overheads.sort((l1, l2) -> -Double.compare(l1, l2));
            t.fifonp_arrivalblocking_overheads = overheads.get(0);
        }

        return local_blocking_each_resource.size() > 0 ? local_blocking_each_resource.get(0) : 0;
    }

    private ArrayList<Resource> FIFONPgetLocalBlockingResources_HI(SporadicTask task, ArrayList<Resource> resources, ArrayList<SporadicTask> localTasks) {
        ArrayList<Resource> localBlockingResources = new ArrayList<>();
        int partition = task.partition;

        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);
//			if (resource.protocol != 2 && resource.protocol != 3) {
            if (resource.protocol == 1) {
                // local resources that have a higher ceiling
                if (resource.partitions.size() == 1 && resource.partitions.get(0) == partition
                        && resource.getCeilingForProcessor(localTasks) >= task.priority) {
                    for (int j = 0; j < resource.requested_tasks.size(); j++) {
                        SporadicTask LP_task = resource.requested_tasks.get(j);
                        if (LP_task.critical==1 && LP_task.partition == partition && LP_task.priority < task.priority) {
                            localBlockingResources.add(resource);
                            break;
                        }
                    }
                }
                // global resources that are accessed from the partition
                if (resource.partitions.contains(partition) && resource.partitions.size() > 1) {
                    for (int j = 0; j < resource.requested_tasks.size(); j++) {
                        SporadicTask LP_task = resource.requested_tasks.get(j);
                        if (LP_task.critical==1 && LP_task.partition == partition && LP_task.priority < task.priority) {
                            localBlockingResources.add(resource);
                            break;
                        }
                    }
                }
            }

        }

        return localBlockingResources;
    }

    private ArrayList<Resource> FIFONPgetLocalBlockingResources_LO(SporadicTask task, ArrayList<Resource> resources, ArrayList<SporadicTask> localTasks) {
        ArrayList<Resource> localBlockingResources = new ArrayList<>();
        int partition = task.partition;

        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);
//			if (resource.protocol != 2 && resource.protocol != 3) {
            if (resource.protocol == 1) {
                // local resources that have a higher ceiling
                if (resource.partitions.size() == 1 && resource.partitions.get(0) == partition
                        && resource.getCeilingForProcessor(localTasks) >= task.priority) {
                    for (int j = 0; j < resource.requested_tasks.size(); j++) {
                        SporadicTask LP_task = resource.requested_tasks.get(j);
                        if (LP_task.critical==0 && LP_task.partition == partition && LP_task.priority < task.priority) {
                            localBlockingResources.add(resource);
                            break;
                        }
                    }
                }
                // global resources that are accessed from the partition
                if (resource.partitions.contains(partition) && resource.partitions.size() > 1) {
                    for (int j = 0; j < resource.requested_tasks.size(); j++) {
                        SporadicTask LP_task = resource.requested_tasks.get(j);
                        if (LP_task.critical==0 && LP_task.partition == partition && LP_task.priority < task.priority) {
                            localBlockingResources.add(resource);
                            break;
                        }
                    }
                }
            }

        }

        return localBlockingResources;
    }

    private long FIFOPlocalBlocking(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> LowTasks,
                                    long[][] Ris, long Ri,
                                    boolean btbHit, boolean useRi) {
        ArrayList<SporadicTask> localTask = tasks.get(t.partition); localTask.addAll(LowTasks.get(t.partition));
        ArrayList<Resource> LocalBlockingResources_HI = FIFOPgetLocalBlockingResources_HI(t, resources, localTask);
        ArrayList<Resource> LocalBlockingResources_LO = FIFOPgetLocalBlockingResources_LO(t, resources, localTask);
        ArrayList<Long> local_blocking_each_resource = new ArrayList<>();

        for (int i = 0; i < LocalBlockingResources_HI.size(); i++) {
            Resource res = LocalBlockingResources_HI.get(i);
            long local_blocking = res.csl_high;
            local_blocking_each_resource.add(local_blocking);
        }
        for (int i = 0; i < LocalBlockingResources_LO.size(); i++) {
            Resource res = LocalBlockingResources_LO.get(i);
            long local_blocking = res.csl_low;
            local_blocking_each_resource.add(local_blocking);
        }

        if (local_blocking_each_resource.size() > 1)
            local_blocking_each_resource.sort((l1, l2) -> -Double.compare(l1, l2));

        if (local_blocking_each_resource.size() > 0)
            t.fifop_arrivalblocking_overheads = AnalysisUtils.FIFOP_LOCK + AnalysisUtils.FIFOP_UNLOCK;

        return local_blocking_each_resource.size() > 0 ? local_blocking_each_resource.get(0) : 0;
    }

    private ArrayList<Resource> FIFOPgetLocalBlockingResources_HI(SporadicTask task, ArrayList<Resource> resources, ArrayList<SporadicTask> localTasks) {
        ArrayList<Resource> localBlockingResources = new ArrayList<>();
        int partition = task.partition;

        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);
            // local resources that have a higher ceiling
            if(resource.protocol >=2 && resource.protocol <= 5){
                if (resource.partitions.size() == 1 && resource.partitions.get(0) == partition
                        && resource.getCeilingForProcessor(localTasks) >= task.priority) {
                    for (int j = 0; j < resource.requested_tasks.size(); j++) {
                        SporadicTask LP_task = resource.requested_tasks.get(j);
                        if (LP_task.critical==1 && LP_task.partition == partition && LP_task.priority < task.priority) {
                            localBlockingResources.add(resource);
                            break;
                        }
                    }
                }
                // global resources that are accessed from the partition
                if (resource.partitions.contains(partition) && resource.partitions.size() > 1) {
                    for (int j = 0; j < resource.requested_tasks.size(); j++) {
                        SporadicTask LP_task = resource.requested_tasks.get(j);
                        if (LP_task.critical==1 && LP_task.partition == partition && LP_task.priority < task.priority) {
                            int rIndex = LP_task.resource_required_index.indexOf(resource.id - 1);
                            int pri = LP_task.resource_required_priority.get(rIndex);
                            if(pri > task.priority){
                                localBlockingResources.add(resource);
                                break;
                            }
                        }
                    }
                }
            }
        }

        return localBlockingResources;
    }

    private ArrayList<Resource> FIFOPgetLocalBlockingResources_LO(SporadicTask task, ArrayList<Resource> resources, ArrayList<SporadicTask> localTasks) {
        ArrayList<Resource> localBlockingResources = new ArrayList<>();
        int partition = task.partition;

        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);
            // local resources that have a higher ceiling
            if(resource.protocol >=2 && resource.protocol <= 5){
                if (resource.partitions.size() == 1 && resource.partitions.get(0) == partition
                        && resource.getCeilingForProcessor(localTasks) >= task.priority) {
                    for (int j = 0; j < resource.requested_tasks.size(); j++) {
                        SporadicTask LP_task = resource.requested_tasks.get(j);
                        if (LP_task.critical==0 && LP_task.partition == partition && LP_task.priority < task.priority) {
                            localBlockingResources.add(resource);
                            break;
                        }
                    }
                }
                // global resources that are accessed from the partition
                if (resource.partitions.contains(partition) && resource.partitions.size() > 1) {
                    for (int j = 0; j < resource.requested_tasks.size(); j++) {
                        SporadicTask LP_task = resource.requested_tasks.get(j);
                        if (LP_task.critical==0 && LP_task.partition == partition && LP_task.priority < task.priority) {
                            int rIndex = LP_task.resource_required_index.indexOf(resource.id - 1);
                            int pri = LP_task.resource_required_priority.get(rIndex);
                            if(pri > task.priority){
                                localBlockingResources.add(resource);
                                break;
                            }
                        }
                    }
                }
            }
        }

        return localBlockingResources;
    }

    private long MrsPlocalBlocking(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, ArrayList<ArrayList<SporadicTask>> LowTasks,
                                   long[][] Ris, long time,
                                   double oneMig, long np, boolean btbHit, boolean useRi) {
        ArrayList<SporadicTask> localTask = tasks.get(t.partition); localTask.addAll(LowTasks.get(t.partition));
        ArrayList<Resource> LocalBlockingResources_HI = MrsPgetLocalBlockingResources_HI(t, resources, localTask);
        ArrayList<Resource> LocalBlockingResources_LO = MrsPgetLocalBlockingResources_LO(t, resources, localTask);

        ArrayList<Long> local_blocking_each_resource = new ArrayList<>();
        ArrayList<Double> overheads = new ArrayList<>();

        for (int i = 0; i < LocalBlockingResources_HI.size(); i++) {
            double arrivalBlockingOverheads = 0;
            ArrayList<Integer> migration_targets = new ArrayList<>();

            Resource res = LocalBlockingResources_HI.get(i);
            long local_blocking = res.csl_high;
            arrivalBlockingOverheads += AnalysisUtils.MrsP_LOCK + AnalysisUtils.MrsP_UNLOCK;

            migration_targets.add(t.partition);
            if (res.isGlobal) {
                int remoteblocking = 0;
                for (int parition_index = 0; parition_index < res.partitions.size(); parition_index++) {
                    int partition = res.partitions.get(parition_index);
                    int norHP = getNoRFromHP(res, t, tasks.get(t.partition), Ris[t.partition], time, btbHit, useRi, false)+
                            getNoRFromHP(res, t, LowTasks.get(t.partition), Ris[t.partition], time, btbHit, useRi, true);
                    int norT = t.resource_required_index.contains(res.id - 1)
                            ? t.number_of_access_in_one_release.get(t.resource_required_index.indexOf(res.id - 1))
                            : 0;
                    int norR_HI = getNoRRemote(res, tasks.get(partition), null, Ris[partition], time, btbHit, useRi);
                    int norR_LO = getNoRRemote(res, null, LowTasks.get(t.partition), Ris[partition], time, btbHit, useRi);

                    if (partition != t.partition && (norHP + norT) < norR_HI+norR_LO) {
                        if(norHP + norT < norR_HI)
                            local_blocking += res.csl_high;
                        else
                            local_blocking += res.csl_low;
                        remoteblocking++;
                        migration_targets.add(partition);
                    }
                }
                arrivalBlockingOverheads += remoteblocking * (AnalysisUtils.MrsP_LOCK + AnalysisUtils.MrsP_UNLOCK);
                double mc_plus = 0;
                if (oneMig != 0) {
                    double mc = migrationCostForArrival(t, res, tasks, LowTasks, migration_targets, oneMig, np, res.csl_high);

                    long mc_long = (long) Math.floor(mc);
                    mc_plus += mc - mc_long;
                    if (mc - mc_long < 0) {
                        System.err.println("MrsP mig error");
                        System.exit(-1);
                    }
                    local_blocking += mc_long;
                }
                arrivalBlockingOverheads += mc_plus;
            }

            local_blocking_each_resource.add(local_blocking);
            overheads.add(arrivalBlockingOverheads);
        }

        for (int i = 0; i < LocalBlockingResources_LO.size(); i++) {
            double arrivalBlockingOverheads = 0;
            ArrayList<Integer> migration_targets = new ArrayList<>();

            Resource res = LocalBlockingResources_LO.get(i);
            long local_blocking = res.csl_low;
            arrivalBlockingOverheads += AnalysisUtils.MrsP_LOCK + AnalysisUtils.MrsP_UNLOCK;

            migration_targets.add(t.partition);
            if (res.isGlobal) {
                int remoteblocking = 0;
                for (int parition_index = 0; parition_index < res.partitions.size(); parition_index++) {
                    int partition = res.partitions.get(parition_index);
                    int norHP = getNoRFromHP(res, t, tasks.get(t.partition), Ris[t.partition], time, btbHit, useRi, false)+
                            getNoRFromHP(res, t, LowTasks.get(t.partition), Ris[t.partition], time, btbHit, useRi, true);
                    int norT = t.resource_required_index.contains(res.id - 1)
                            ? t.number_of_access_in_one_release.get(t.resource_required_index.indexOf(res.id - 1))
                            : 0;
                    int norR_HI = getNoRRemote(res, tasks.get(partition), null, Ris[partition], time, btbHit, useRi);
                    int norR_LO = getNoRRemote(res, null, LowTasks.get(t.partition), Ris[partition], time, btbHit, useRi);

                    if (partition != t.partition && (norHP + norT) < norR_HI+norR_LO) {
                        if(norHP + norT < norR_HI)
                            local_blocking += res.csl_high;
                        else
                            local_blocking += res.csl_low;
                        remoteblocking++;
                        migration_targets.add(partition);
                    }
                }
                arrivalBlockingOverheads += remoteblocking * (AnalysisUtils.MrsP_LOCK + AnalysisUtils.MrsP_UNLOCK);
                double mc_plus = 0;
                if (oneMig != 0) {
                    double mc = migrationCostForArrival(t, res, tasks, LowTasks, migration_targets, oneMig, np, res.csl_low);

                    long mc_long = (long) Math.floor(mc);
                    mc_plus += mc - mc_long;
                    if (mc - mc_long < 0) {
                        System.err.println("MrsP mig error");
                        System.exit(-1);
                    }
                    local_blocking += mc_long;
                }
                arrivalBlockingOverheads += mc_plus;
            }

            local_blocking_each_resource.add(local_blocking);
            overheads.add(arrivalBlockingOverheads);
        }

        if (local_blocking_each_resource.size() >= 1) {
            if (overheads.size() <= 0) {
                System.err.println("overheads error!");
                System.exit(-1);
            }
            local_blocking_each_resource.sort((l1, l2) -> -Double.compare(l1, l2));
            overheads.sort((l1, l2) -> -Double.compare(l1, l2));
            t.mrsp_arrivalblocking_overheads = overheads.get(0);
        }

        return local_blocking_each_resource.size() > 0 ? local_blocking_each_resource.get(0) : 0;

    }
    private double migrationCostForArrival(SporadicTask calT, Resource resource, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<ArrayList<SporadicTask>> LowTasks,
                                           ArrayList<Integer> migration_targets,
                                           double oneMig, long np, double duration) {
        return migrationCost(calT, resource, tasks, LowTasks, migration_targets, oneMig, np, duration);
    }
    private ArrayList<Resource> MrsPgetLocalBlockingResources_HI(SporadicTask task, ArrayList<Resource> resources, ArrayList<SporadicTask> localTasks) {
        ArrayList<Resource> localBlockingResources = new ArrayList<>();
        int partition = task.partition;

        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);

            if (resource.protocol == 6 && resource.partitions.contains(partition) && resource.getCeilingForProcessor(localTasks) >= task.priority) {
                for (int j = 0; j < resource.requested_tasks.size(); j++) {
                    SporadicTask LP_task = resource.requested_tasks.get(j);
                    if (LP_task.critical==1 && LP_task.partition == partition && LP_task.priority < task.priority) {
                        localBlockingResources.add(resource);
                        break;
                    }
                }
            }

            int max_pri = getMaxPriorityOfLocalTasks(localTasks);
            int ceiling_pri = resource.getCeilingForProcessor(localTasks);
            int compare_pri = (int) Math.ceil(ceiling_pri + ((double)(max_pri - ceiling_pri) / 2.0));

            if (resource.protocol == 7 && resource.partitions.contains(partition) && compare_pri >= task.priority) {
                for (int j = 0; j < resource.requested_tasks.size(); j++) {
                    SporadicTask LP_task = resource.requested_tasks.get(j);
                    if (LP_task.critical==1 && LP_task.partition == partition && LP_task.priority < task.priority) {
                        localBlockingResources.add(resource);
                        break;
                    }
                }
            }
        }

        return localBlockingResources;
    }
    private ArrayList<Resource> MrsPgetLocalBlockingResources_LO(SporadicTask task, ArrayList<Resource> resources, ArrayList<SporadicTask> localTasks) {
        ArrayList<Resource> localBlockingResources = new ArrayList<>();
        int partition = task.partition;

        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);

            if (resource.protocol == 6 && resource.partitions.contains(partition) && resource.getCeilingForProcessor(localTasks) >= task.priority) {
                for (int j = 0; j < resource.requested_tasks.size(); j++) {
                    SporadicTask LP_task = resource.requested_tasks.get(j);
                    if (LP_task.critical==0 && LP_task.partition == partition && LP_task.priority < task.priority) {
                        localBlockingResources.add(resource);
                        break;
                    }
                }
            }

            int max_pri = getMaxPriorityOfLocalTasks(localTasks);
            int ceiling_pri = resource.getCeilingForProcessor(localTasks);
            int compare_pri = (int) Math.ceil(ceiling_pri + ((double)(max_pri - ceiling_pri) / 2.0));

            if (resource.protocol == 7 && resource.partitions.contains(partition) && compare_pri >= task.priority) {
                for (int j = 0; j < resource.requested_tasks.size(); j++) {
                    SporadicTask LP_task = resource.requested_tasks.get(j);
                    if (LP_task.critical==0 && LP_task.partition == partition && LP_task.priority < task.priority) {
                        localBlockingResources.add(resource);
                        break;
                    }
                }
            }
        }

        return localBlockingResources;
    }
    private int getMaxPriorityOfLocalTasks(ArrayList<SporadicTask> tasks){
        int max_priority = -1;
        for(SporadicTask t: tasks){
            if(t.priority > max_priority){
                max_priority = t.priority;
            }
        }
        return max_priority;
    }

    private boolean isTaskIncurNPSection(SporadicTask task, ArrayList<SporadicTask> tasksOnItsParititon, ArrayList<Resource> resources) {
        int partition = task.partition;
        int priority = task.priority;
        int minCeiling = 1000;

        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);
            // int ceiling = resource.getCeilingForProcessor(tasksOnItsParititon);
            int max_pri = getMaxPriorityOfLocalTasks(tasksOnItsParititon);
            int ceiling_pri = resource.getCeilingForProcessor(tasksOnItsParititon);
            int compare_pri = (int) Math.ceil(ceiling_pri + ((double)(max_pri - ceiling_pri) / 2.0));
            if (resource.protocol == 6 && resource.partitions.contains(partition) && minCeiling > ceiling_pri) {
                minCeiling = ceiling_pri;
            }
            if (resource.protocol == 7 && resource.partitions.contains(partition) && minCeiling > compare_pri) {
                minCeiling = compare_pri;
            }
        }

        if (priority > minCeiling)
            return true;
        else
            return false;
    }
}