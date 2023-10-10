package com.demo.tool.responsetimeanalysis.analysis;

import  com.demo.tool.responsetimeanalysis.entity.Resource;
import  com.demo.tool.responsetimeanalysis.entity.SporadicTask;
import  com.demo.tool.responsetimeanalysis.generator.PriorityGenerator;
import  com.demo.tool.responsetimeanalysis.utils.AnalysisUtils;

import java.util.*;
import java.util.stream.Collectors;

public class DynamicAnalysis {
    private static long[] checkCondition(ArrayList<ArrayList<ArrayList<Long>>> history, int size) {
        if (history.size() < size) {
            return null;
        }
        ArrayList<Long> differ_list = new ArrayList<Long>();
        Integer DIFFER_A_INDEX = null,DIFFER_B_INDEX = null;
        for(int i = 0 ; i < history.size() - 1; i++){
            ArrayList<ArrayList<Long>> Compare_A = history.get(i);
            ArrayList<ArrayList<Long>> Compare_B = history.get(i+1);
            int differ = 0;
            Long DIFFER_A = null,DIFFER_B = null;

            for(int j = 0 ; j < Compare_A.size(); j++){
                for(int k = 0 ; k < Compare_A.get(j).size(); k++){
                    Long A = Compare_A.get(j).get(k);
                    Long B = Compare_B.get(j).get(k);
                    if(!Objects.equals(A, B)){
                        DIFFER_A = A;
                        DIFFER_A_INDEX = j;
                        DIFFER_B = B;
                        DIFFER_B_INDEX = k;
                        differ++;
                    }
                }
            }

            if(differ != 1){
                history.remove(0);
                return null;
            } else{
                if(i == 0){
                    differ_list.add(DIFFER_A);
                    differ_list.add(DIFFER_B);
                } else{
                    differ_list.add(DIFFER_B);
                }
            }
        }

        for(int i = 0 ; i < differ_list.size() - 2; i++){
            if(!Objects.equals(differ_list.get(i), differ_list.get(i + 2))){
                history.remove(0);
                return null;
            }
        }
        history.remove(0);
        long[] re = new long[3];
        re[0] = DIFFER_A_INDEX;
        re[1] = DIFFER_B_INDEX;
        re[2] = (long)Math.ceil((differ_list.get(0) + differ_list.get(1)) / 2.0);
        return re;
    }


    /** for stable mode**/
    // random the protocol for each resource -> update resource request priority
    public long[][] getResponseTimeByDMPO(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, int extendCal, boolean testSchedulability,
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
            long[][] response_time_plus = busyWindow(tasks, resources, response_time, AnalysisUtils.MrsP_PREEMPTION_AND_MIGRATION, np, extendCal,
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

    private long[][] busyWindow(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] response_time, double oneMig, long np,
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

                response_time_plus[i][j] = oneCalculation(task, tasks, resources, response_time, response_time[i][j], oneMig, np, btbHit, useRi);

                if (testSchedulability && task.Ri > task.deadline) {
                    return response_time_plus;
                }
            }
        }
        return response_time_plus;
    }

    private long oneCalculation(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] response_time, long Ri,
                                double oneMig, long np, boolean btbHit, boolean useRi) {

        task.Ri = task.spin = task.interference = task.local = task.indirect_spin = task.total_blocking = 0;
        task.np_section = task.blocking_overheads = task.implementation_overheads = task.migration_overheads_plus = 0;
        task.mrsp_arrivalblocking_overheads = task.fifonp_arrivalblocking_overheads = task.fifop_arrivalblocking_overheads = 0;
        task.test_delay = 0;

        task.implementation_overheads += AnalysisUtils.FULL_CONTEXT_SWTICH1;

        task.spin = resourceAccessingTime(task, tasks, resources, response_time, Ri, oneMig, np, btbHit, useRi, task);
        task.interference = highPriorityInterference(task, tasks, resources, response_time, Ri, oneMig, np, btbHit, useRi);
        task.local = localBlocking(task, tasks, resources, response_time, Ri, oneMig, np, btbHit, useRi);

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
    private long resourceAccessingTime(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris, long time,
                                       double oneMig, long np, boolean btbHit, boolean useRi, SporadicTask calT) {
        long resourceTime = 0;
        resourceTime += FIFONPResourceTime(task, tasks, resources, Ris, time, btbHit, useRi);
        resourceTime += FIFOPResourceAccessTime(task, tasks, resources, Ris, time, btbHit, useRi);
        resourceTime += MrsPresourceAccessingTime(task, tasks, resources, Ris, time, 0, oneMig, np, btbHit, useRi, calT);
        return resourceTime;
    }

    /**
     * FIFO-NP resource accessing time.
     */
    private long FIFONPResourceTime(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris, long Ri,
                                    boolean btbHit, boolean useRi) {
        long spin_delay = 0;
        for (int k = 0; k < t.resource_required_index.size(); k++) {
            Resource resource = resources.get(t.resource_required_index.get(k));
            if (resource.protocol == 1) {
                long NoS = getNoSpinDelay(t, resource, tasks, Ris, Ri, btbHit, useRi);
                spin_delay += NoS * resource.csl;
                t.implementation_overheads += (NoS + t.number_of_access_in_one_release.get(t.resource_required_index.indexOf(resource.id - 1)))
                        * (AnalysisUtils.FIFONP_LOCK + AnalysisUtils.FIFONP_UNLOCK);
                t.blocking_overheads += NoS * (AnalysisUtils.FIFONP_LOCK + AnalysisUtils.FIFONP_UNLOCK);

                spin_delay += resource.csl * t.number_of_access_in_one_release.get(t.resource_required_index.indexOf(resource.id - 1));
            }

        }
        return spin_delay;
    }

    /*
     * gives the number of requests from remote partitions for a resource that
     * is required by the given task.
     */
    private int getNoSpinDelay(SporadicTask task, Resource resource, ArrayList<ArrayList<SporadicTask>> tasks, long[][] Ris, long Ri, boolean btbHit,
                               boolean useRi) {
        int number_of_spin_dealy = 0;

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
                int getNoRFromHP = getNoRFromHP(resource, task, tasks.get(task.partition), Ris[task.partition], Ri, btbHit, useRi);
                int possible_spin_delay = number_of_request_by_Remote_P - getNoRFromHP < 0 ? 0 : number_of_request_by_Remote_P - getNoRFromHP;

                int NoRFromT = task.number_of_access_in_one_release.get(getIndexRInTask(task, resource));
                number_of_spin_dealy += Integer.min(possible_spin_delay, NoRFromT);
            }
        }
        return number_of_spin_dealy;
    }

    /**
     * FIFO-P resource accessing time.
     */
    private long FIFOPResourceAccessTime(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris, long time,
                                         boolean btbHit, boolean useRi) {
        long spin = 0;
        ArrayList<ArrayList<Long>> requestsLeftOnRemoteP = new ArrayList<>();
        ArrayList<Resource> fifo_resources = new ArrayList<>();
        for (int i = 0; i < resources.size(); i++) {
            Resource res = resources.get(i);
            // 控制FIFO-P相关的protocol
            if (res.protocol >= 2 && res.protocol <= 5) {
                requestsLeftOnRemoteP.add(new ArrayList<Long>());
                fifo_resources.add(res);
                spin += getSpinDelayForOneResoruce(task, tasks, res, requestsLeftOnRemoteP.get(requestsLeftOnRemoteP.size() - 1), Ris, time, btbHit, useRi);
            } else{
                requestsLeftOnRemoteP.add(new ArrayList<Long>());
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
                    pp.put(high_task_priority,preemptions);
                }
            }
            task.implementation_overheads += sum_preemptions * (AnalysisUtils.FIFOP_CANCEL);
            task.blocking_overheads += sum_preemptions * (AnalysisUtils.FIFOP_CANCEL);
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

            for(int p = 0 ; p < requestsLeftOnRemoteP.size(); p++){
                ArrayList<Long> rlorp = new ArrayList<>(requestsLeftOnRemoteP.get(p));
                ArrayList<Long> csl_queue = new ArrayList<>();
                while(rlorp.size() != 0){
                    csl_queue.add(rlorp.size() * resources.get(p).csl);
                    for(int q = 0; q < rlorp.size(); q++){
                        rlorp.set(q, rlorp.get(q) - 1);
                        if (rlorp.get(q) < 1) {
                            rlorp.remove(q);
                            q--;
                        }
                    }
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
            if(sortedwww.size() > 0){
                List<Map.Entry<Set<Integer>, Long>> entryList = new ArrayList<>(sortedwww.entrySet());
                Long sum_preempt = 0L;
                // 迭代过程优化
                for(int i = 0 ; i < entryList.size(); i++){
                    Map.Entry<Set<Integer>, Long> A = entryList.get(i);
                    Map.Entry<Set<Integer>, Long> B = i == entryList.size() -1 ? null : entryList.get(i+1);
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
                    ArrayList<Long> subList = new ArrayList<>(sum_array.subList(0, (int)Math.min(sum_array.size(),sum_preempt)));
                    sum_array = subList;
                }
            }
            for(Long num : sum_array) {
                spin += num;
            }

            task.spin_delay_by_preemptions = request_by_preemptions;

        }
        return spin;
    }

    private long getSpinDelayForOneResoruce(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, Resource resource,
                                            ArrayList<Long> requestsLeftOnRemoteP, long[][] Ris, long time, boolean btbHit, boolean useRi) {
        long spin = 0;
        long ncs = 0;

        for (int i = 0; i < tasks.get(task.partition).size(); i++) {
            SporadicTask hpTask = tasks.get(task.partition).get(i);
            if (hpTask.priority > task.priority && hpTask.resource_required_index.contains(resource.id - 1)) {
                ncs += (long) Math.ceil((double) (time + (btbHit ? (useRi ? Ris[task.partition][i] : hpTask.deadline) : 0)) / (double) hpTask.period)
                        * hpTask.number_of_access_in_one_release.get(hpTask.resource_required_index.indexOf(resource.id - 1));
            }
        }

        if (task.resource_required_index.contains(resource.id - 1))
            ncs += task.number_of_access_in_one_release.get(task.resource_required_index.indexOf(resource.id - 1));

        if (ncs > 0) {
            for (int i = 0; i < tasks.size(); i++) {
                if (task.partition != i) {
                    /* For each remote partition */
                    long number_of_request_by_Remote_P = 0;
                    for (int j = 0; j < tasks.get(i).size(); j++) {
                        if (tasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
                            SporadicTask remote_task = tasks.get(i).get(j);
                            int indexR = getIndexRInTask(remote_task, resource);
                            int number_of_release = (int) Math
                                    .ceil((double) (time + (btbHit ? (useRi ? Ris[i][j] : remote_task.deadline) : 0)) / (double) remote_task.period);
                            number_of_request_by_Remote_P += (long) number_of_release * remote_task.number_of_access_in_one_release.get(indexR);
                        }
                    }

                    long possible_spin_delay = Long.min(number_of_request_by_Remote_P, ncs);
                    spin += possible_spin_delay;
                    if (number_of_request_by_Remote_P - ncs > 0)
                        requestsLeftOnRemoteP.add(number_of_request_by_Remote_P - ncs);
                }
            }
        }

        task.implementation_overheads += (spin + ncs) * (AnalysisUtils.FIFOP_LOCK + AnalysisUtils.FIFOP_UNLOCK);
        task.blocking_overheads += (spin + ncs
                - (task.resource_required_index.contains(resource.id - 1)
                ? task.number_of_access_in_one_release.get(task.resource_required_index.indexOf(resource.id - 1))
                : 0))
                * (AnalysisUtils.FIFOP_LOCK + AnalysisUtils.FIFOP_UNLOCK);
        return spin * resource.csl + ncs * resource.csl;
    }

    /**
     * MrsP resource accessing time.
     */
    private long MrsPresourceAccessingTime(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris, long time,
                                           long jitter, double oneMig, long np, boolean btbHit, boolean useRi, SporadicTask calT) {
        long resource_accessing_time = 0;

        for (int i = 0; i < task.resource_required_index.size(); i++) {
            Resource resource = resources.get(task.resource_required_index.get(i));

            if (resource.protocol == 6 || resource.protocol == 7) {
                // 这里的jitter为0
                int number_of_request_with_btb = (int) Math.ceil((double) (time + jitter) / (double) task.period) * task.number_of_access_in_one_release.get(i);

                for (int j = 1; j < number_of_request_with_btb + 1; j++) {
                    long oneAccess = 0;
                    oneAccess += MrsPresourceAccessingTimeInOne(task, resource, tasks, Ris, time, jitter, j, btbHit, useRi, calT);

                    if (oneMig != 0) {
                        double mc = migrationCostForSpin(task, resource, tasks, Ris, time, j, oneMig, np, btbHit, useRi, calT);
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

    private long MrsPresourceAccessingTimeInOne(SporadicTask task, Resource resource, ArrayList<ArrayList<SporadicTask>> tasks, long[][] Ris, long time,
                                                long jitter, int request_index, boolean btbHit, boolean useRi, SporadicTask calTask) {
        int number_of_access = 0;

        for (int i = 0; i < tasks.size(); i++) {
            if (i != task.partition) {
                /* For each remote partition */
                int number_of_request_by_Remote_P = 0;
                for (int j = 0; j < tasks.get(i).size(); j++) {
                    if (tasks.get(i).get(j).resource_required_index.contains(resource.id - 1)) {
                        SporadicTask remote_task = tasks.get(i).get(j);
                        int indexR = getIndexRInTask(remote_task, resource);
                        int number_of_release = (int) Math
                                .ceil((double) (time + (btbHit ? (useRi ? Ris[i][j] : remote_task.deadline) : 0)) / (double) remote_task.period);
                        number_of_request_by_Remote_P += number_of_release * remote_task.number_of_access_in_one_release.get(indexR);
                    }
                }
                int getNoRFromHP = getNoRFromHP(resource, task, tasks.get(task.partition), Ris[task.partition], time, btbHit, useRi);
                int possible_spin_delay = number_of_request_by_Remote_P - getNoRFromHP - request_index + 1 < 0 ? 0
                        : number_of_request_by_Remote_P - getNoRFromHP - request_index + 1;
                number_of_access += Integer.min(possible_spin_delay, 1);
            }
        }

        // account for the request of the task itself
        number_of_access++;

        calTask.implementation_overheads += number_of_access * (AnalysisUtils.MrsP_LOCK + AnalysisUtils.MrsP_UNLOCK);
        calTask.blocking_overheads += (number_of_access - 1) * (AnalysisUtils.MrsP_LOCK + AnalysisUtils.MrsP_UNLOCK);

        return number_of_access * resource.csl;
    }

    /***************************************************
     ************* InDirect Spin Delay *******************
     ***************************************************/
    private long highPriorityInterference(SporadicTask t, ArrayList<ArrayList<SporadicTask>> allTasks, ArrayList<Resource> resources, long[][] Ris, long time,
                                          double oneMig, long np, boolean btbHit, boolean useRi) {
        long interference = 0;
        int partition = t.partition;
        ArrayList<SporadicTask> tasks = allTasks.get(partition);

        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).priority > t.priority) {
                SporadicTask hpTask = tasks.get(i);
                interference += Math.ceil((double) (time) / (double) hpTask.period) * (hpTask.WCET);
                t.implementation_overheads += Math.ceil((double) (time) / (double) hpTask.period) * (AnalysisUtils.FULL_CONTEXT_SWTICH2);

                long btb_interference = getIndirectSpinDelay(hpTask, allTasks, resources, Ris, time, Ris[partition][i], btbHit, useRi, t);
                interference += MrsPresourceAccessingTime(hpTask, allTasks, resources, Ris, time, btbHit ? (useRi ? Ris[partition][i] : hpTask.deadline) : 0,
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
    private long getIndirectSpinDelay(SporadicTask hpTask, ArrayList<ArrayList<SporadicTask>> allTasks, ArrayList<Resource> resources, long[][] Ris, long Ri,
                                      long Rihp, boolean btbHit, boolean useRi, SporadicTask calTask) {
        long BTBhit = 0;

        for (int i = 0; i < hpTask.resource_required_index.size(); i++) {
            /* for each resource that a high priority task request */
            Resource resource = resources.get(hpTask.resource_required_index.get(i));

//			if (resource.protocol != 2 && resource.protocol != 3) {
            if (resource.protocol == 1) {
                int number_of_higher_request = getNoRFromHP(resource, hpTask, allTasks.get(hpTask.partition), Ris[hpTask.partition], Ri, btbHit, useRi);
                int number_of_request_with_btb = (int) Math.ceil((double) (Ri + (btbHit ? (useRi ? Rihp : hpTask.deadline) : 0)) / (double) hpTask.period)
                        * hpTask.number_of_access_in_one_release.get(i);

                BTBhit += number_of_request_with_btb * resource.csl;
                calTask.implementation_overheads += number_of_request_with_btb * (AnalysisUtils.FIFONP_LOCK + AnalysisUtils.FIFONP_UNLOCK);
                calTask.blocking_overheads += number_of_request_with_btb * (AnalysisUtils.FIFONP_LOCK + AnalysisUtils.FIFONP_UNLOCK);

                for (int j = 0; j < resource.partitions.size(); j++) {
                    if (resource.partitions.get(j) != hpTask.partition) {
                        int remote_partition = resource.partitions.get(j);
                        int number_of_remote_request = getNoRRemote(resource, allTasks.get(remote_partition), Ris[remote_partition], Ri, btbHit, useRi);

                        int possible_spin_delay = number_of_remote_request - number_of_higher_request < 0 ? 0
                                : number_of_remote_request - number_of_higher_request;

                        int spin_delay_with_btb = Integer.min(possible_spin_delay, number_of_request_with_btb);

                        BTBhit += spin_delay_with_btb * resource.csl;
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
    private long localBlocking(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris, long time, double oneMig,
                               long np, boolean btbHit, boolean useRi) {
        long localblocking = 0;
        long fifonp_localblocking = FIFONPlocalBlocking(t, tasks, resources, Ris, time, btbHit, useRi);
        long fifop_localblocking = FIFOPlocalBlocking(t, tasks, resources, Ris, time, btbHit, useRi);
        long MrsP_localblocking = MrsPlocalBlocking(t, tasks, resources, Ris, time, oneMig, np, btbHit, useRi);
        long npsection = (isTaskIncurNPSection(t, tasks.get(t.partition), resources) ? np : 0);

        ArrayList<Double> blocking = new ArrayList<>();

        double fifonp = t.fifonp_arrivalblocking_overheads + fifonp_localblocking;
        double fifop = t.fifop_arrivalblocking_overheads + fifop_localblocking;
        double mrsp = t.mrsp_arrivalblocking_overheads + MrsP_localblocking;

        blocking.add(fifonp);
        blocking.add(fifop);
        blocking.add(mrsp);
        blocking.add((double) npsection);

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

    private long FIFONPlocalBlocking(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris, long Ri,
                                     boolean btbHit, boolean useRi) {
        ArrayList<Resource> LocalBlockingResources = FIFONPgetLocalBlockingResources(t, resources, tasks.get(t.partition));
        ArrayList<Long> local_blocking_each_resource = new ArrayList<>();
        ArrayList<Double> overheads = new ArrayList<>();

        for (int i = 0; i < LocalBlockingResources.size(); i++) {
            Resource res = LocalBlockingResources.get(i);
            long local_blocking = res.csl;

            if (res.isGlobal) {
                for (int parition_index = 0; parition_index < res.partitions.size(); parition_index++) {
                    int partition = res.partitions.get(parition_index);
                    int norHP = getNoRFromHP(res, t, tasks.get(t.partition), Ris[t.partition], Ri, btbHit, useRi);
                    int norT = t.resource_required_index.contains(res.id - 1)
                            ? t.number_of_access_in_one_release.get(t.resource_required_index.indexOf(res.id - 1))
                            : 0;
                    int norR = getNoRRemote(res, tasks.get(partition), Ris[partition], Ri, btbHit, useRi);

                    if (partition != t.partition && (norHP + norT) < norR) {
                        local_blocking += res.csl;
                    }
                }
            }
            local_blocking_each_resource.add(local_blocking);
            overheads.add((local_blocking / res.csl) * (AnalysisUtils.FIFONP_LOCK + AnalysisUtils.FIFONP_UNLOCK));
        }

        if (local_blocking_each_resource.size() >= 1) {
            local_blocking_each_resource.sort((l1, l2) -> -Double.compare(l1, l2));
            overheads.sort((l1, l2) -> -Double.compare(l1, l2));
            t.fifonp_arrivalblocking_overheads = overheads.get(0);
        }

        return local_blocking_each_resource.size() > 0 ? local_blocking_each_resource.get(0) : 0;
    }

    private ArrayList<Resource> FIFONPgetLocalBlockingResources(SporadicTask task, ArrayList<Resource> resources, ArrayList<SporadicTask> localTasks) {
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
                        if (LP_task.partition == partition && LP_task.priority < task.priority) {
                            localBlockingResources.add(resource);
                            break;
                        }
                    }
                }
                // global resources that are accessed from the partition
                if (resource.partitions.contains(partition) && resource.partitions.size() > 1) {
                    for (int j = 0; j < resource.requested_tasks.size(); j++) {
                        SporadicTask LP_task = resource.requested_tasks.get(j);
                        if (LP_task.partition == partition && LP_task.priority < task.priority) {
                            localBlockingResources.add(resource);
                            break;
                        }
                    }
                }
            }

        }

        return localBlockingResources;
    }

    private long FIFOPlocalBlocking(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris, long Ri,
                                    boolean btbHit, boolean useRi) {
        ArrayList<Resource> LocalBlockingResources = FIFOPgetLocalBlockingResources(t, resources, tasks.get(t.partition));
        ArrayList<Long> local_blocking_each_resource = new ArrayList<>();

        for (int i = 0; i < LocalBlockingResources.size(); i++) {
            Resource res = LocalBlockingResources.get(i);
            long local_blocking = res.csl;
            local_blocking_each_resource.add(local_blocking);
        }

        if (local_blocking_each_resource.size() > 1)
            local_blocking_each_resource.sort((l1, l2) -> -Double.compare(l1, l2));

        if (local_blocking_each_resource.size() > 0)
            t.fifop_arrivalblocking_overheads = AnalysisUtils.FIFOP_LOCK + AnalysisUtils.FIFOP_UNLOCK;

        return local_blocking_each_resource.size() > 0 ? local_blocking_each_resource.get(0) : 0;
    }

    private ArrayList<Resource> FIFOPgetLocalBlockingResources(SporadicTask task, ArrayList<Resource> resources, ArrayList<SporadicTask> localTasks) {
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
                        if (LP_task.partition == partition && LP_task.priority < task.priority) {
                            localBlockingResources.add(resource);
                            break;
                        }
                    }
                }
                // global resources that are accessed from the partition
                if (resource.partitions.contains(partition) && resource.partitions.size() > 1) {
                    for (int j = 0; j < resource.requested_tasks.size(); j++) {
                        SporadicTask LP_task = resource.requested_tasks.get(j);
                        if (LP_task.partition == partition && LP_task.priority < task.priority) {
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

//	private ArrayList<Resource> FIFOPgetLocalBlockingResources(SporadicTask task, ArrayList<Resource> resources, ArrayList<SporadicTask> localTasks) {
//		ArrayList<Resource> localBlockingResources = new ArrayList<>();
//		int partition = task.partition;
//
//		for (int i = 0; i < resources.size(); i++) {
//			Resource resource = resources.get(i);
//			if (resource.protocol == 2) {
//				// local resources that have a higher ceiling
//				if (resource.partitions.size() == 1 && resource.partitions.get(0) == partition
//						&& resource.getCeilingForProcessor(localTasks) >= task.priority) {
//					for (int j = 0; j < resource.requested_tasks.size(); j++) {
//						SporadicTask LP_task = resource.requested_tasks.get(j);
//						if (LP_task.partition == partition && LP_task.priority < task.priority) {
//							localBlockingResources.add(resource);
//							break;
//						}
//					}
//				}
//				// global resources that are accessed from the partition
//				if (resource.partitions.contains(partition) && resource.partitions.size() > 1) {
//					for (int j = 0; j < resource.requested_tasks.size(); j++) {
//						SporadicTask LP_task = resource.requested_tasks.get(j);
//						if (LP_task.partition == partition && LP_task.priority < task.priority) {
//							localBlockingResources.add(resource);
//							break;
//						}
//					}
//				}
//			}
//
//		}
//
//		return localBlockingResources;
//	}

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

    private long MrsPlocalBlocking(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris, long time,
                                   double oneMig, long np, boolean btbHit, boolean useRi) {
        ArrayList<Resource> LocalBlockingResources = MrsPgetLocalBlockingResources(t, resources, tasks.get(t.partition));
        ArrayList<Long> local_blocking_each_resource = new ArrayList<>();
        ArrayList<Double> overheads = new ArrayList<>();

        for (int i = 0; i < LocalBlockingResources.size(); i++) {
            double arrivalBlockingOverheads = 0;
            ArrayList<Integer> migration_targets = new ArrayList<>();

            Resource res = LocalBlockingResources.get(i);
            long local_blocking = res.csl;
            arrivalBlockingOverheads += AnalysisUtils.MrsP_LOCK + AnalysisUtils.MrsP_UNLOCK;

            migration_targets.add(t.partition);
            if (res.isGlobal) {
                int remoteblocking = 0;
                for (int parition_index = 0; parition_index < res.partitions.size(); parition_index++) {
                    int partition = res.partitions.get(parition_index);
                    int norHP = getNoRFromHP(res, t, tasks.get(t.partition), Ris[t.partition], time, btbHit, useRi);
                    int norT = t.resource_required_index.contains(res.id - 1)
                            ? t.number_of_access_in_one_release.get(t.resource_required_index.indexOf(res.id - 1))
                            : 0;
                    int norR = getNoRRemote(res, tasks.get(partition), Ris[partition], time, btbHit, useRi);

                    if (partition != t.partition && (norHP + norT) < norR) {
                        local_blocking += res.csl;
                        remoteblocking++;
                        migration_targets.add(partition);
                    }
                }
                arrivalBlockingOverheads += remoteblocking * (AnalysisUtils.MrsP_LOCK + AnalysisUtils.MrsP_UNLOCK);
                double mc_plus = 0;
                if (oneMig != 0) {
                    double mc = migrationCostForArrival(t, res, tasks, migration_targets, oneMig, np);

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

    private ArrayList<Resource> MrsPgetLocalBlockingResources(SporadicTask task, ArrayList<Resource> resources, ArrayList<SporadicTask> localTasks) {
        ArrayList<Resource> localBlockingResources = new ArrayList<>();
        int partition = task.partition;

        for (int i = 0; i < resources.size(); i++) {
            Resource resource = resources.get(i);

            if (resource.protocol == 6 && resource.partitions.contains(partition) && resource.getCeilingForProcessor(localTasks) >= task.priority) {
                for (int j = 0; j < resource.requested_tasks.size(); j++) {
                    SporadicTask LP_task = resource.requested_tasks.get(j);
                    if (LP_task.partition == partition && LP_task.priority < task.priority) {
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
                    if (LP_task.partition == partition && LP_task.priority < task.priority) {
                        localBlockingResources.add(resource);
                        break;
                    }
                }
            }
        }

        return localBlockingResources;
    }

    /***************************************************
     ************* Migration Cost Calculation **********
     ***************************************************/
    private double migrationCostForSpin(SporadicTask task, Resource resource, ArrayList<ArrayList<SporadicTask>> tasks, long[][] Ris, long time,
                                        int request_number, double oneMig, long np, boolean btbHit, boolean useRi, SporadicTask calT) {

        ArrayList<Integer> migration_targets = new ArrayList<>();

        // identify the migration targets
        migration_targets.add(task.partition);
        for (int i = 0; i < tasks.size(); i++) {
            if (i != task.partition) {
                int number_requests_left = 0;
                number_requests_left = getNoRRemote(resource, tasks.get(i), Ris[i], time, btbHit, useRi)
                        - getNoRFromHP(resource, task, tasks.get(task.partition), Ris[task.partition], time, btbHit, useRi) - request_number + 1;

                if (number_requests_left > 0)
                    migration_targets.add(i);
            }
        }

        return migrationCost(calT, resource, tasks, migration_targets, oneMig, np);
    }

    private double migrationCostForArrival(SporadicTask calT, Resource resource, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Integer> migration_targets,
                                           double oneMig, long np) {
        return migrationCost(calT, resource, tasks, migration_targets, oneMig, np);
    }

    private int getMaxPriorityOfPartition(ArrayList<ArrayList<SporadicTask>> tasks, int partition){
        ArrayList<SporadicTask> taskset = tasks.get(partition);
        int max_priority = -1;
        for(SporadicTask t: taskset){
            if(t.priority > max_priority){
                max_priority = t.priority;
            }
        }
        return max_priority;
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

    private double migrationCost(SporadicTask calT, Resource resource, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Integer> migration_targets,
                                 double oneMig, long np) {
        double migrationCost = 0;
        ArrayList<Integer> migration_targets_with_P = new ArrayList<>();

        // identify the migration targets with preemptors
        for (int i = 0; i < migration_targets.size(); i++) {
            int partition = migration_targets.get(i);

            if(resource.protocol == 6){
                if (tasks.get(partition).get(0).priority > resource.getCeilingForProcessor(tasks, partition))
                    migration_targets_with_P.add(migration_targets.get(i));
            } else if (resource.protocol == 7) {
                int max_pri = getMaxPriorityOfPartition(tasks,partition);
                int ceiling_pri = resource.getCeilingForProcessor(tasks, partition);
                int compare_pri = (int) Math.ceil(ceiling_pri + ((double)(max_pri - ceiling_pri) / 2.0));
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
                    double migCostWithNP = (long) (1 + Math.ceil((double) resource.csl / (double) np)) * oneMig;
                    double migCostWithHP = migrationCostBusyWindow(migration_targets_with_P, oneMig, resource, tasks, calT, migCostWithNP);
                    migration_cost_for_one_access = Math.min(migCostWithHP, migCostWithNP);
                } else {
                    migration_cost_for_one_access = migrationCostBusyWindow(migration_targets_with_P, oneMig, resource, tasks, calT, -1);
                }
            }

            migrationCost += migration_cost_for_one_access;
        }

        return migrationCost;
    }

    private double migrationCostBusyWindow(ArrayList<Integer> migration_targets_with_P, double oneMig, Resource resource,
                                           ArrayList<ArrayList<SporadicTask>> tasks, SporadicTask calT, double migByNP) {
        double migCost = 0;

        double newMigCost = migrationCostOneCal(migration_targets_with_P, oneMig, resource.csl + migCost, resource, tasks);

        while (migCost != newMigCost) {
            migCost = newMigCost;
            newMigCost = migrationCostOneCal(migration_targets_with_P, oneMig, resource.csl + migCost, resource, tasks);

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

                // 条件修改

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
    /*
     * gives that number of requests from HP local tasks for a resource that is
     * required by the given task.
     */
    private int getNoRFromHP(Resource resource, SporadicTask task, ArrayList<SporadicTask> tasks, long[] Ris, long Ri, boolean btbHit, boolean useRi) {
        int number_of_request_by_HP = 0;
        int priority = task.priority;

        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).priority > priority && tasks.get(i).resource_required_index.contains(resource.id - 1)) {
                SporadicTask hpTask = tasks.get(i);
                int indexR = getIndexRInTask(hpTask, resource);
                number_of_request_by_HP += Math.ceil((double) (Ri + (btbHit ? (useRi ? Ris[i] : hpTask.deadline) : 0)) / (double) hpTask.period)
                        * hpTask.number_of_access_in_one_release.get(indexR);
            }
        }
        return number_of_request_by_HP;
    }

    private int getNoRRemote(Resource resource, ArrayList<SporadicTask> tasks, long[] Ris, long Ri, boolean btbHit, boolean useRi) {
        int number_of_request_by_Remote_P = 0;

        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).resource_required_index.contains(resource.id - 1)) {
                SporadicTask remote_task = tasks.get(i);
                int indexR = getIndexRInTask(remote_task, resource);
                number_of_request_by_Remote_P += Math.ceil((double) (Ri + (btbHit ? (useRi ? Ris[i] : remote_task.deadline) : 0)) / (double) remote_task.period)
                        * remote_task.number_of_access_in_one_release.get(indexR);
            }
        }
        return number_of_request_by_Remote_P;
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
