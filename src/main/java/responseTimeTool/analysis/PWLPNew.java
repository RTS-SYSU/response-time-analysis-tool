package responseTimeTool.analysis;

import responseTimeTool.entity.Resource;
import responseTimeTool.entity.SporadicTask;
import responseTimeTool.utils.AnalysisUtils;

import java.util.ArrayList;

public class PWLPNew {
	long count = 0;

	public long[][] getResponseTime(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, boolean btbHit, boolean printDebug) {
		long[][] init_Ri = new AnalysisUtils().initResponseTime(tasks);

		long[][] response_time = new long[tasks.size()][];
		boolean isEqual = false, missDeadline = false;
		count = 0;

		for (int i = 0; i < init_Ri.length; i++) {
			response_time[i] = new long[init_Ri[i].length];
		}

		new AnalysisUtils().cloneList(init_Ri, response_time);

		/* a huge busy window to get a fixed Ri */
		while (!isEqual) {
			isEqual = true;
			long[][] response_time_plus = busyWindow(tasks, resources, response_time, btbHit);

			for (int i = 0; i < response_time_plus.length; i++) {
				for (int j = 0; j < response_time_plus[i].length; j++) {
					if (response_time[i][j] != response_time_plus[i][j])
						isEqual = false;
					if (response_time_plus[i][j] > tasks.get(i).get(j).deadline)
						missDeadline = true;
				}
			}

			count++;
			new AnalysisUtils().cloneList(response_time_plus, response_time);

			if (missDeadline)
				break;
		}

		if (printDebug) {
			if (missDeadline)
				System.out.println("FIFO-P-NEW    after " + count + " tims of recursion, the tasks miss the deadline.");
			else
				System.out.println("FIFO-P-NEW    after " + count + " tims of recursion, we got the response time.");
			new AnalysisUtils().printResponseTime(response_time, tasks);
		}

		return response_time;
	}

	private long[][] busyWindow(ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] response_time, boolean btbHit) {
		long[][] response_time_plus = new long[tasks.size()][];

		for (int i = 0; i < response_time.length; i++) {
			response_time_plus[i] = new long[response_time[i].length];
		}

		for (int i = 0; i < tasks.size(); i++) {
			for (int j = 0; j < tasks.get(i).size(); j++) {
				SporadicTask task = tasks.get(i).get(j);
				task.spin_delay_by_preemptions = 0;
				task.spin = getSpinDelay(task, tasks, resources, response_time[i][j], response_time, btbHit);
				task.interference = highPriorityInterference(task, tasks, response_time[i][j], response_time, resources);
				task.local = localBlocking(task, tasks, resources, response_time, response_time[i][j]);
				task.indirect_spin = highPriorityIndirectSpin(task, tasks, response_time[i][j]);

				response_time_plus[i][j] = task.Ri = task.WCET + task.spin + task.interference + task.local;

				if (task.Ri > task.deadline)
					return response_time_plus;

			}
		}
		return response_time_plus;
	}

	private long getSpinDelay(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long time, long[][] Ris,
			boolean btbHit) {
		long spin = 0;
		ArrayList<ArrayList<Long>> requestsLeftOnRemoteP = new ArrayList<>();
		for (int i = 0; i < resources.size(); i++) {
			requestsLeftOnRemoteP.add(new ArrayList<Long>());
			Resource res = resources.get(i);
			spin += getSpinDelayForOneResoruce(task, tasks, res, time, Ris, requestsLeftOnRemoteP.get(i), btbHit);
		}

		// preemptions
		long preemptions = 0;
		long request_by_preemptions = 0;
		for (int i = 0; i < tasks.get(task.partition).size(); i++) {
			if (tasks.get(task.partition).get(i).priority > task.priority) {
				preemptions += (int) Math.ceil((double) (time) / (double) tasks.get(task.partition).get(i).period);
			}
		}

		while (preemptions > 0) {

			long max_delay = 0;
			int max_delay_resource_index = -1;
			for (int i = 0; i < resources.size(); i++) {
				if (max_delay < resources.get(i).csl * requestsLeftOnRemoteP.get(i).size()) {
					max_delay = resources.get(i).csl * requestsLeftOnRemoteP.get(i).size();
					max_delay_resource_index = i;
				}
			}

			if (max_delay > 0) {
				spin += max_delay;
				for (int i = 0; i < requestsLeftOnRemoteP.get(max_delay_resource_index).size(); i++) {
					requestsLeftOnRemoteP.get(max_delay_resource_index).set(i, requestsLeftOnRemoteP.get(max_delay_resource_index).get(i) - 1);
					if (requestsLeftOnRemoteP.get(max_delay_resource_index).get(i) < 1) {
						requestsLeftOnRemoteP.get(max_delay_resource_index).remove(i);
						i--;
					}
				}
				preemptions--;
				request_by_preemptions++;
			} else
				break;
		}

		task.spin_delay_by_preemptions = request_by_preemptions;

		return spin;
	}

	private long getSpinDelayForOneResoruce(SporadicTask task, ArrayList<ArrayList<SporadicTask>> tasks, Resource resource, long time, long[][] Ris,
			ArrayList<Long> requestsLeftOnRemoteP, boolean btbHit) {
		long spin = 0;
		long ncs = 0;

		for (int i = 0; i < tasks.get(task.partition).size(); i++) {
			SporadicTask hpTask = tasks.get(task.partition).get(i);
			if (hpTask.priority > task.priority && hpTask.resource_required_index.contains(resource.id - 1)) {
				ncs += (int) Math.ceil((double) (time + (btbHit ? Ris[hpTask.partition][i] : 0)) / (double) hpTask.period)
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
							int number_of_release = (int) Math.ceil((double) (time + (btbHit ? Ris[i][j] : 0)) / (double) remote_task.period);
							number_of_request_by_Remote_P += number_of_release * remote_task.number_of_access_in_one_release.get(indexR);
						}
					}

					long possible_spin_delay = Long.min(number_of_request_by_Remote_P, ncs);
					spin += possible_spin_delay;
					if (number_of_request_by_Remote_P - ncs > 0)
						requestsLeftOnRemoteP.add(number_of_request_by_Remote_P - ncs);
				}
			}
		}

		return spin * resource.csl + ncs * resource.csl;
	}

	/*
	 * Calculate the local high priority tasks' interference for a given task t.
	 * CI is a set of computation time of local tasks, including spin delay.
	 */
	private long highPriorityInterference(SporadicTask t, ArrayList<ArrayList<SporadicTask>> allTasks, long time, long[][] Ris, ArrayList<Resource> resources) {
		long interference = 0;
		int partition = t.partition;
		ArrayList<SporadicTask> tasks = allTasks.get(partition);

		for (int i = 0; i < tasks.size(); i++) {
			if (tasks.get(i).priority > t.priority) {
				SporadicTask hpTask = tasks.get(i);
				interference += Math.ceil((double) (time) / (double) hpTask.period) * (hpTask.WCET);
			}
		}
		return interference;
	}

	/*
	 *
	 * Calculate the indirect spin
	 *
	 */
	private long highPriorityIndirectSpin(SporadicTask t, ArrayList<ArrayList<SporadicTask>> allTasks, long Ri) {
		long indirect_spin = 0;
		int partition = t.partition;
		ArrayList<SporadicTask> tasks = allTasks.get(partition);

		for (int i = 0; i < tasks.size(); i++) {
			if (tasks.get(i).priority > t.priority) {
				SporadicTask hpTask = tasks.get(i);
				indirect_spin += Math.ceil((double) (Ri) / (double) hpTask.period) * hpTask.spin;
			}
		}
		return indirect_spin;
	}

	private long localBlocking(SporadicTask t, ArrayList<ArrayList<SporadicTask>> tasks, ArrayList<Resource> resources, long[][] Ris, long Ri) {
		ArrayList<Resource> LocalBlockingResources = getLocalBlockingResources(t, resources, tasks.get(t.partition));
		ArrayList<Long> local_blocking_each_resource = new ArrayList<>();

		for (int i = 0; i < LocalBlockingResources.size(); i++) {
			Resource res = LocalBlockingResources.get(i);
			long local_blocking = res.csl;
			local_blocking_each_resource.add(local_blocking);
		}

		if (local_blocking_each_resource.size() > 1)
			local_blocking_each_resource.sort((l1, l2) -> -Double.compare(l1, l2));

		return local_blocking_each_resource.size() > 0 ? local_blocking_each_resource.get(0) : 0;
	}

	private ArrayList<Resource> getLocalBlockingResources(SporadicTask task, ArrayList<Resource> resources, ArrayList<SporadicTask> localTasks) {
		ArrayList<Resource> localBlockingResources = new ArrayList<>();
		int partition = task.partition;

		for (int i = 0; i < resources.size(); i++) {
			Resource resource = resources.get(i);
			// local resources that have a higher ceiling
			if (resource.partitions.size() == 1 && resource.partitions.get(0) == partition
					&&  resource.getCeilingForProcessor(localTasks) >= task.priority) {
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

		return localBlockingResources;
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
