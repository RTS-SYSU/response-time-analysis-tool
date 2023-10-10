package com.demo.tool.responsetimeanalysis.generator;

import com.demo.tool.responsetimeanalysis.entity.Resource;
import com.demo.tool.responsetimeanalysis.entity.SporadicTask;

import java.util.ArrayList;
import java.util.Comparator;

public class AllocationGeneator {
	public ArrayList<ArrayList<SporadicTask>> allocateTasks(ArrayList<SporadicTask> tasksToAllocate, ArrayList<Resource> resources, int total_partitions,
															int policy) {

		int Osize = tasksToAllocate.size();
		double totalUtil = 0.0;
		for (SporadicTask sporadicTask : tasksToAllocate) {
			totalUtil += sporadicTask.util;
		}
		
		//double totalUtil = 0.05 * tasksToAllocate.size();

		double maxUtilPerCore;
		if (totalUtil / total_partitions < 0.5)
			maxUtilPerCore = 0.5;
		else if (totalUtil / total_partitions < 0.6)
			maxUtilPerCore = 0.6;
		else if (totalUtil / total_partitions < 0.65)
			maxUtilPerCore = 0.65;
		else
			maxUtilPerCore = totalUtil / total_partitions <= 0.9 ? (totalUtil / total_partitions) + 0.05 : 1;



		ArrayList<ArrayList<SporadicTask>> tasks = switch (policy) {
			case 0 -> WF(tasksToAllocate, total_partitions);
			case 1 -> BF(tasksToAllocate, total_partitions, maxUtilPerCore);
			case 2 -> FF(tasksToAllocate, total_partitions, maxUtilPerCore);
			case 3 -> NF(tasksToAllocate, total_partitions, maxUtilPerCore);
			case 4 -> SPA(tasksToAllocate, resources, total_partitions, maxUtilPerCore);
			case 5 -> RCF(tasksToAllocate, resources, total_partitions, maxUtilPerCore);
			default -> null;
		};

		if (tasks != null) {
			for (int i = 0; i < tasks.size(); i++) {
				if (tasks.get(i).size() == 0) {
					tasks.remove(i);
					i--;
				}
			}

			for (int i = 0; i < tasks.size(); i++) {
				for (int j = 0; j < tasks.get(i).size(); j++) {
					tasks.get(i).get(j).partition = i;
				}
			}

			if (resources != null && resources.size() > 0) {
				for (Resource res : resources) {
					res.isGlobal = false;
					res.partitions.clear();
					res.requested_tasks.clear();
				}

				/* for each resource */
				for (Resource resource : resources) {
					/* for each partition */
					for (ArrayList<SporadicTask> sporadicTasks : tasks) {

						/* for each task in the given partition */
						for (SporadicTask task : sporadicTasks) {
							if (task.resource_required_index.contains(resource.id - 1)) {
								resource.requested_tasks.add(task);
								if (!resource.partitions.contains(task.partition)) {
									resource.partitions.add(task.partition);
								}
							}
						}
					}

					if (resource.partitions.size() > 1)
						resource.isGlobal = true;
				}
			}

		}

		if (tasks != null) {
			int Nsize = 0;
			for (ArrayList<SporadicTask> task : tasks) {
				Nsize += task.size();
			}

			if (Osize != Nsize) {
				System.err.println("Allocation error!");
			}
		}

		return tasks;
	}

	private ArrayList<ArrayList<SporadicTask>> WF(ArrayList<SporadicTask> tasksToAllocate, int partitions) {
		// clear tasks' partitions
		for (SporadicTask sporadicTask : tasksToAllocate) {
			sporadicTask.partition = -1;
		}

		tasksToAllocate.sort((p1, p2) -> -Double.compare(p1.util, p2.util));

		// Init allocated tasks array
		ArrayList<ArrayList<SporadicTask>> tasks = new ArrayList<>();
		for (int i = 0; i < partitions; i++) {
			ArrayList<SporadicTask> task = new ArrayList<>();
			tasks.add(task);
		}

		// init util array
		ArrayList<Double> utilPerPartition = new ArrayList<>();
		for (int i = 0; i < partitions; i++) {
			utilPerPartition.add((double) 0);
		}

		for (SporadicTask task : tasksToAllocate) {
			int target = -1;
			double minUtil = 2;
			for (int j = 0; j < partitions; j++) {
				if (minUtil > utilPerPartition.get(j)) {
					minUtil = utilPerPartition.get(j);
					target = j;
				}
			}

			if (target == -1) {
				System.err.println("WF error!");
				return null;
			}

			if ((double) 1 - minUtil >= task.util) {
				task.partition = target;
				utilPerPartition.set(target, utilPerPartition.get(target) + task.util);
			} else
				return null;
		}

		for (SporadicTask sporadicTask : tasksToAllocate) {
			int partition = sporadicTask.partition;
			tasks.get(partition).add(sporadicTask);
		}

		for (ArrayList<SporadicTask> task : tasks) {
			task.sort(Comparator.comparingDouble(p -> p.period));
		}

		return tasks;
	}

	private ArrayList<ArrayList<SporadicTask>> BF(ArrayList<SporadicTask> tasksToAllocate, int partitions, double maxUtilPerCore) {

		for (SporadicTask sporadicTask : tasksToAllocate) {
			sporadicTask.partition = -1;
		}
		tasksToAllocate.sort((p1, p2) -> -Double.compare(p1.util, p2.util));

		ArrayList<ArrayList<SporadicTask>> tasks = new ArrayList<>();
		for (int i = 0; i < partitions; i++) {
			ArrayList<SporadicTask> task = new ArrayList<>();
			tasks.add(task);
		}

		ArrayList<Double> utilPerPartition = new ArrayList<>();
		for (int i = 0; i < partitions; i++) {
			utilPerPartition.add((double) 0);
		}

		for (SporadicTask task : tasksToAllocate) {
			int target = -1;
			double maxUtil = -1;
			for (int j = 0; j < partitions; j++) {
				if (maxUtil < utilPerPartition.get(j) && ((maxUtilPerCore - utilPerPartition.get(j) >= task.util)
						|| (task.util > maxUtilPerCore && 1 - utilPerPartition.get(j) >= task.util))) {
					maxUtil = utilPerPartition.get(j);
					target = j;
				}
			}

			if (target < 0) {
				return null;
			} else {
				task.partition = target;
				utilPerPartition.set(target, utilPerPartition.get(target) + task.util);
			}
		}

		for (SporadicTask sporadicTask : tasksToAllocate) {
			int partition = sporadicTask.partition;
			tasks.get(partition).add(sporadicTask);
		}

		for (ArrayList<SporadicTask> task : tasks) {
			task.sort(Comparator.comparingDouble(p -> p.period));
		}

		return tasks;
	}

	private ArrayList<ArrayList<SporadicTask>> FF(ArrayList<SporadicTask> tasksToAllocate, int partitions, double maxUtilPerCore) {

		for (int i = 0; i < tasksToAllocate.size(); i++) {
			tasksToAllocate.get(i).partition = -1;
		}
		tasksToAllocate.sort((p1, p2) -> -Double.compare(p1.util, p2.util));

		ArrayList<ArrayList<SporadicTask>> tasks = new ArrayList<>();
		for (int i = 0; i < partitions; i++) {
			ArrayList<SporadicTask> task = new ArrayList<>();
			tasks.add(task);
		}

		ArrayList<Double> utilPerPartition = new ArrayList<>();
		for (int i = 0; i < partitions; i++) {
			utilPerPartition.add((double) 0);
		}

		for (int i = 0; i < tasksToAllocate.size(); i++) {
			SporadicTask task = tasksToAllocate.get(i);
			for (int j = 0; j < partitions; j++) {
				if ((maxUtilPerCore - utilPerPartition.get(j) >= task.util) || (task.util > maxUtilPerCore && 1 - utilPerPartition.get(j) >= task.util)) {
					task.partition = j;
					utilPerPartition.set(j, utilPerPartition.get(j) + task.util);
					break;
				}
			}
			if (task.partition == -1)
				return null;
		}

		for (int i = 0; i < tasksToAllocate.size(); i++) {
			int partition = tasksToAllocate.get(i).partition;
			tasks.get(partition).add(tasksToAllocate.get(i));
		}

		for (int i = 0; i < tasks.size(); i++) {
			tasks.get(i).sort((p1, p2) -> Double.compare(p1.period, p2.period));
		}

		return tasks;
	}

	private ArrayList<ArrayList<SporadicTask>> NF(ArrayList<SporadicTask> tasksToAllocate, int partitions, double maxUtilPerCore) {

		for (int i = 0; i < tasksToAllocate.size(); i++) {
			tasksToAllocate.get(i).partition = -1;
		}
		tasksToAllocate.sort((p1, p2) -> -Double.compare(p1.util, p2.util));

		ArrayList<ArrayList<SporadicTask>> tasks = new ArrayList<>();
		for (int i = 0; i < partitions; i++) {
			ArrayList<SporadicTask> task = new ArrayList<>();
			tasks.add(task);
		}

		ArrayList<Double> utilPerPartition = new ArrayList<>();
		for (int i = 0; i < partitions; i++) {
			utilPerPartition.add((double) 0);
		}

		int currentIndex = 0;

		for (int i = 0; i < tasksToAllocate.size(); i++) {
			SporadicTask task = tasksToAllocate.get(i);

			for (int j = 0; j < partitions; j++) {
				if ((maxUtilPerCore - utilPerPartition.get(currentIndex) >= task.util)
						|| (task.util > maxUtilPerCore && 1 - utilPerPartition.get(j) >= task.util)) {
					task.partition = currentIndex;
					utilPerPartition.set(currentIndex, utilPerPartition.get(currentIndex) + task.util);
					break;
				}
				if (currentIndex == partitions - 1)
					currentIndex = 0;
				else
					currentIndex++;
			}
			if (task.partition == -1)
				return null;
		}

		for (int i = 0; i < tasksToAllocate.size(); i++) {
			int partition = tasksToAllocate.get(i).partition;
			tasks.get(partition).add(tasksToAllocate.get(i));
		}

		for (int i = 0; i < tasks.size(); i++) {
			tasks.get(i).sort((p1, p2) -> Double.compare(p1.period, p2.period));
		}

		return tasks;
	}

	public ArrayList<ArrayList<SporadicTask>> RUF(ArrayList<SporadicTask> tasksToAllocate, ArrayList<Resource> resources, int partitions,
			double maxUtilPerCore) {
		for (int i = 0; i < tasksToAllocate.size(); i++) {
			tasksToAllocate.get(i).partition = -1;
		}

		int number_of_resources = resources.size();

		ArrayList<ArrayList<Double>> NoQT = new ArrayList<>();
		for (int i = 0; i < number_of_resources; i++) {
			ArrayList<Double> noq = new ArrayList<>();
			noq.add((double) i);
			noq.add((double) 0);
			NoQT.add(noq);
		}

		for (int j = 0; j < tasksToAllocate.size(); j++) {
			SporadicTask task = tasksToAllocate.get(j);
			for (int k = 0; k < task.resource_required_index.size(); k++) {
				NoQT.get(task.resource_required_index.get(k)).set(1, NoQT.get(task.resource_required_index.get(k)).get(1) + task.util);
			}
		}

		NoQT.sort((p1, p2) -> -Double.compare(p1.get(1), p2.get(1)));

		ArrayList<SporadicTask> sortedTasks = new ArrayList<>();
		ArrayList<SporadicTask> cleanTasks = new ArrayList<>();
		for (int i = 0; i < NoQT.size(); i++) {
			for (int j = 0; j < tasksToAllocate.size(); j++) {
				SporadicTask task = tasksToAllocate.get(j);
				double index = NoQT.get(i).get(0);
				int intIndex = (int) index;
				if (task.resource_required_index.contains(intIndex) && !sortedTasks.contains(task)) {
					sortedTasks.add(task);
				}
				if (!cleanTasks.contains(task) && task.resource_required_index.size() == 0) {
					cleanTasks.add(task);
				}
			}
		}
		sortedTasks.addAll(cleanTasks);

		if (sortedTasks.size() != tasksToAllocate.size()) {
			System.out.println("RESOURCE REQUEST FIT sorted tasks size error!");
			System.exit(-1);
		}

		return NF(sortedTasks, partitions, maxUtilPerCore);
	}

	private ArrayList<ArrayList<SporadicTask>> SPA(ArrayList<SporadicTask> tasksToAllocate, ArrayList<Resource> resources, int partitions,
			double maxUtilPerCore) {
		for (int i = 0; i < tasksToAllocate.size(); i++) {
			tasksToAllocate.get(i).partition = -1;
		}

		/* Resources are grouped via the bundling approach. */
		ArrayList<Resource> resource_copy = new ArrayList<>(resources);
		ArrayList<ArrayList<Resource>> resourceBundles = new ArrayList<>();

		for (int i = 0; i < resources.size(); i++) {
			Resource res1 = resources.get(i);
			int index = getIndex(resourceBundles, res1);
			if (index == -1) {
				ArrayList<Resource> ress = new ArrayList<>();
				ress.add(res1);
				resource_copy.remove(resource_copy.indexOf(res1));
				resourceBundles.add(ress);
				index = resourceBundles.size() - 1;
			}

			for (int j = i + 1; j < resources.size(); j++) {
				Resource res2 = resources.get(j);
				int isIn = getIndex(resourceBundles, res2);
				boolean isContain = isContain(res1, res2);
				if (isIn == -1 && isContain) {
					resourceBundles.get(index).add(res2);
					resource_copy.remove(resource_copy.indexOf(res2));
				}
			}
		}

		if (resource_copy.size() != 0) {
			System.err.println("Resource grouping error!");
			System.exit(-1);
		}
		int resNum = 0;
		for (int i = 0; i < resourceBundles.size(); i++) {
			resNum += resourceBundles.get(i).size();
		}
		if (resNum != resources.size()) {
			System.err.println("Resource number error!");
			System.exit(-1);
		}
		for (int i = 0; i < resources.size() - 1; i++) {
			Resource res1 = resources.get(i);

			for (int j = i + 1; j < resources.size(); j++) {
				Resource res2 = resources.get(j);
				if (res1.id == res2.id) {
					System.err.println("Resource Identical error!");
					System.exit(-1);
				}
			}
		}

		/* Then, tasks are bundled via the resource grouping */
		ArrayList<ArrayList<SporadicTask>> taskBundles = new ArrayList<>();
		ArrayList<SporadicTask> tasks_copy = new ArrayList<>(tasksToAllocate);

		for (int i = 0; i < resourceBundles.size(); i++) {
			ArrayList<SporadicTask> taskBundle = new ArrayList<>();

			for (int j = 0; j < resourceBundles.get(i).size(); j++) {
				Resource res = resourceBundles.get(i).get(j);

				for (int k = 0; k < tasks_copy.size(); k++) {
					SporadicTask t = tasks_copy.get(k);
					if (t.resource_required_index.contains(res.id - 1)) {
						if (!taskBundle.contains(t)) {
							taskBundle.add(t);
							tasks_copy.remove(tasks_copy.indexOf(t));
							k--;
						} else {
							tasks_copy.remove(tasks_copy.indexOf(t));
							k--;
						}
					}
				}
			}
			taskBundles.add(taskBundle);
		}

		ArrayList<SporadicTask> independentTasks = new ArrayList<>();
		for (int i = 0; i < tasks_copy.size(); i++) {
			SporadicTask task = tasks_copy.get(i);
			if (task.resource_required_index.size() == 0) {
				independentTasks.add(task);
				tasks_copy.remove(i);
				i--;
			}
		}

		if (tasks_copy.size() != 0) {
			System.err.println("Task grouping error!");
			System.exit(-1);
		}

		int taskNum = independentTasks.size();
		for (int i = 0; i < taskBundles.size(); i++) {
			taskNum += taskBundles.get(i).size();
		}

		if (taskNum != tasksToAllocate.size()) {
			System.err.println("Task number error!");
			System.exit(-1);
		}

		/* Sort independent tasks and bundles via decreasing utilization */
		independentTasks.sort((t1, t2) -> -Double.compare(t1.util, t2.util));
		taskBundles.sort((l1, l2) -> {
			Double tu1 = l1.stream().mapToDouble(t -> t.util).sum();
			Double tu2 = l2.stream().mapToDouble(t -> t.util).sum();
			return -Double.compare(tu1, tu2);
		});

		/* Get allocatable bundles and tasks */
		ArrayList<ArrayList<SporadicTask>> allocatableTasks = new ArrayList<>();
		for (int i = 0; i < taskBundles.size(); i++) {
			Double tu1 = taskBundles.get(i).stream().mapToDouble(t -> t.util).sum();
			if (tu1 <= maxUtilPerCore) {
				allocatableTasks.add(taskBundles.get(i));
				taskBundles.remove(i);
				i--;
			}
		}
		for (int i = 0; i < independentTasks.size(); i++) {
			ArrayList<SporadicTask> task = new ArrayList<>();
			task.add(independentTasks.get(i));
			allocatableTasks.add(task);
		}

		/*
		 * Now, order the unallocatable bundles and tasks in each bundle via
		 * increasing utilisation
		 */
		// taskBundles.sort((l1, l2) -> {
		// Double tu1 = l1.stream().mapToDouble(t -> t.util).sum();
		// Double tu2 = l2.stream().mapToDouble(t -> t.util).sum();
		// return Double.compare(tu1, tu2);
		// });
		taskBundles.sort((t1, t2) -> compareSlack(t1, t2, resources));
		for (int i = 0; i < taskBundles.size(); i++) {
			taskBundles.get(i).sort((t1, t2) -> Double.compare(t1.util, t2.util));
		}

		/*
		 * Add the unallocatable bundles to the each of the to-be-allocated list
		 */
		for (int i = 0; i < taskBundles.size(); i++) {
			for (int j = 0; j < taskBundles.get(i).size(); j++) {
				ArrayList<SporadicTask> task = new ArrayList<>();
				task.add(taskBundles.get(i).get(j));
				allocatableTasks.add(task);
			}
		}

		/* Allocate the ordered tasks and bundles via BF */
		ArrayList<ArrayList<SporadicTask>> tasks = new ArrayList<>();
		for (int i = 0; i < partitions; i++) {
			ArrayList<SporadicTask> task = new ArrayList<>();
			tasks.add(task);
		}

		ArrayList<Double> utilPerPartition = new ArrayList<>();
		for (int i = 0; i < partitions; i++) {
			utilPerPartition.add((double) 0);
		}

		for (int i = 0; i < allocatableTasks.size(); i++) {
			ArrayList<SporadicTask> task = allocatableTasks.get(i);
			Double util = task.stream().mapToDouble(t -> t.util).sum();
			int target = -1;
			double maxUtil = -1;
			for (int j = 0; j < partitions; j++) {
				if (maxUtil < utilPerPartition.get(j)
						&& ((maxUtilPerCore - utilPerPartition.get(j) >= util) || (util > maxUtilPerCore && 1 - utilPerPartition.get(j) >= util))) {
					maxUtil = utilPerPartition.get(j);
					target = j;
				}
			}

			if (target < 0) {
				return null;
			} else {
				for (int j = 0; j < task.size(); j++) {
					task.get(j).partition = target;
				}
				tasks.get(target).addAll(task);
				utilPerPartition.set(target, utilPerPartition.get(target) + util);
			}
		}

		for (int i = 0; i < tasks.size(); i++) {
			tasks.get(i).sort((p1, p2) -> Double.compare(p1.period, p2.period));
		}

		return tasks;
	}

	private int compareSlack(ArrayList<SporadicTask> t1, ArrayList<SporadicTask> t2, ArrayList<Resource> res) {
		double costT1 = 0, costT2 = 0;

		ArrayList<Resource> res1 = new ArrayList<>();
		ArrayList<Resource> res2 = new ArrayList<>();

		for (int i = 0; i < t1.size(); i++) {
			SporadicTask task = t1.get(i);
			ArrayList<Integer> resource_index = task.resource_required_index;
			for (int j = 0; j < resource_index.size(); j++) {
				Resource resource = res.get(resource_index.get(j));
				if (!res1.contains(resource))
					res1.add(resource);
			}
		}

		for (int i = 0; i < t2.size(); i++) {
			SporadicTask task = t2.get(i);
			ArrayList<Integer> resource_index = task.resource_required_index;
			for (int j = 0; j < resource_index.size(); j++) {
				Resource resource = res.get(resource_index.get(j));
				if (!res2.contains(resource))
					res2.add(resource);
			}
		}

		for (int i = 0; i < res1.size(); i++) {
			Resource resource = res1.get(i);
			ArrayList<SporadicTask> tasks = resource.requested_tasks;
			long minT = 1000;

			for (int j = 0; j < tasks.size(); j++) {
				if (minT > tasks.get(j).period)
					minT = tasks.get(j).period;
			}
			costT1 += (double) resource.csl / (double) minT;
		}

		for (int i = 0; i < res2.size(); i++) {
			Resource resource = res2.get(i);
			ArrayList<SporadicTask> tasks = resource.requested_tasks;
			long minT = 1000;

			for (int j = 0; j < tasks.size(); j++) {
				if (minT > tasks.get(j).period)
					minT = tasks.get(j).period;
			}
			costT2 += (double) resource.csl / (double) minT;
		}

		if (costT1 <= costT2) {
			return -1;
		}

		if (costT1 > costT2) {
			return 1;
		}

		// if (costT1 == costT1) {
		// if (deadline1 < deadline2)
		// return -1;
		// if (deadline1 > deadline2)
		// return 1;
		// if (deadline1 == deadline2)
		// return 0;
		// }

		// System.err
		// .println("Slack comparator error!" + " slack1: " + slack1 + "
		// deadline1: " + deadline1 + " slack2: " + slack2 + " deadline2: " +
		// deadline2);
		System.exit(-1);
		return 0;
	}

	private boolean isContain(Resource res1, Resource res2) {

		ArrayList<SporadicTask> task1 = res1.requested_tasks;
		ArrayList<SporadicTask> task2 = res2.requested_tasks;

		for (int i = 0; i < task1.size(); i++) {
			SporadicTask t1 = task1.get(i);
			for (int j = 0; j < task2.size(); j++) {

				SporadicTask t2 = task2.get(j);
				if (t1.id == t2.id) {
					return true;
				}
			}
		}

		return false;
	}

	private int getIndex(ArrayList<ArrayList<Resource>> resourceBundles, Resource res) {
		for (int i = 0; i < resourceBundles.size(); i++) {
			if (resourceBundles.get(i).contains(res))
				return i;
		}
		return -1;
	}

	private ArrayList<ArrayList<SporadicTask>> RCF(ArrayList<SporadicTask> tasksToAllocate, ArrayList<Resource> resources, int partitions,
			double maxUtilPerCore) {
		for (int i = 0; i < tasksToAllocate.size(); i++) {
			tasksToAllocate.get(i).partition = -1;
		}

		tasksToAllocate.sort((p1, p2) -> Double.compare(p1.util, p2.util));

		int number_of_resources = resources.size();

		ArrayList<ArrayList<Integer>> NoQT = new ArrayList<>();
		//资源长度的二维数组？
		for (int i = 0; i < number_of_resources; i++) {
			ArrayList<Integer> noq = new ArrayList<>();
			noq.add(i);	//记录排名？
			noq.add(0);	//	计数
			NoQT.add(noq);
		}

		for (int j = 0; j < tasksToAllocate.size(); j++) {
			SporadicTask task = tasksToAllocate.get(j);
			for (int k = 0; k < task.resource_required_index.size(); k++) {//遍历任务所需资源
				NoQT.get(task.resource_required_index.get(k)).set(1,
						NoQT.get(task.resource_required_index.get(k)).get(1) + task.number_of_access_in_one_release.get(k));
				// TODO whether by task number or request number?
			}
		}

		NoQT.sort((p1, p2) -> -Double.compare(p1.get(1), p2.get(1)));

		ArrayList<SporadicTask> sortedTasks = new ArrayList<>();
		ArrayList<SporadicTask> cleanTasks = new ArrayList<>();
		for (int i = 0; i < NoQT.size(); i++) {
			for (int j = 0; j < tasksToAllocate.size(); j++) {
				SporadicTask task = tasksToAllocate.get(j);
				if (task.resource_required_index.contains(NoQT.get(i).get(0)) && !sortedTasks.contains(task)) {
					sortedTasks.add(task);
				}
				if (!cleanTasks.contains(task) && task.resource_required_index.size() == 0) {
					cleanTasks.add(task);
				}
			}
		}

		if (sortedTasks.size() + cleanTasks.size() != tasksToAllocate.size()) {
			System.out.println("RESOURCE REQUEST FIT sorted tasks size error!");
			System.exit(-1);
		}

		ArrayList<ArrayList<SporadicTask>> tasks = NF(sortedTasks, partitions, maxUtilPerCore);
		if (tasks == null)
			return null;

		cleanTasks.sort((p1, p2) -> -Double.compare(p1.util, p2.util));

		// init util array
		ArrayList<Double> utilPerPartition = new ArrayList<>();
		for (int i = 0; i < partitions; i++) {
			Double tu1 = tasks.get(i).stream().mapToDouble(t -> t.util).sum();
			utilPerPartition.add(tu1);
		}

		for (int i = 0; i < cleanTasks.size(); i++) {
			SporadicTask task = cleanTasks.get(i);
			int target = -1;
			double minUtil = 2;
			for (int j = 0; j < partitions; j++) {
				if (minUtil > utilPerPartition.get(j)) {
					minUtil = utilPerPartition.get(j);
					target = j;
				}
			}

			if (target == -1) {
				System.err.println("WF error!");
				return null;
			}

			if ((double) 1 - minUtil >= task.util) {
				task.partition = target;
				utilPerPartition.set(target, utilPerPartition.get(target) + task.util);
			} else
				return null;
		}

		for (int i = 0; i < cleanTasks.size(); i++) {
			int partition = cleanTasks.get(i).partition;
			tasks.get(partition).add(cleanTasks.get(i));
		}

		for (int i = 0; i < tasks.size(); i++) {
			tasks.get(i).sort((p1, p2) -> Double.compare(p1.period, p2.period));
		}

		return tasks;
	}

	private ArrayList<ArrayList<SporadicTask>> RLFL(ArrayList<SporadicTask> tasksToAllocate, ArrayList<Resource> resources, int partitions,
			double maxUtilPerCore) {
		ArrayList<SporadicTask> unallocT = new ArrayList<>(tasksToAllocate);

		for (int i = 0; i < unallocT.size(); i++) {
			unallocT.get(i).partition = -1;
		}

		ArrayList<SporadicTask> sortedT = new ArrayList<>();

		for (int i = 0; i < resources.size(); i++) {
			Resource res = resources.get(i);
			ArrayList<SporadicTask> resT = new ArrayList<>();
			for (int j = 0; j < unallocT.size(); j++) {
				if (unallocT.get(j).resource_required_index.contains(res.id - 1)) {
					resT.add(unallocT.get(j));
					unallocT.remove(j);
					j--;
				}
			}
			resT.sort((p1, p2) -> Double.compare(p1.util, p2.util));
			sortedT.addAll(resT);
		}

		if (sortedT.size() + unallocT.size() != tasksToAllocate.size()) {
			System.err.println("resource len decrease: alloc and unalloc tasks size error!");
			System.exit(-1);
		}

		ArrayList<ArrayList<SporadicTask>> tasks = NF(sortedT, partitions, maxUtilPerCore);
		if (tasks == null)
			return null;
		unallocT.sort((p1, p2) -> -Double.compare(p1.util, p2.util));

		// init util array
		ArrayList<Double> utilPerPartition = new ArrayList<>();
		for (int i = 0; i < partitions; i++) {
			Double tu1 = tasks.get(i).stream().mapToDouble(t -> t.util).sum();
			utilPerPartition.add(tu1);
		}

		for (int i = 0; i < unallocT.size(); i++) {
			SporadicTask task = unallocT.get(i);
			int target = -1;
			double minUtil = 2;
			for (int j = 0; j < partitions; j++) {
				if (minUtil > utilPerPartition.get(j)) {
					minUtil = utilPerPartition.get(j);
					target = j;
				}
			}

			if (target == -1) {
				System.err.println("WF error!");
				return null;
			}

			if ((double) 1 - minUtil >= task.util) {
				task.partition = target;
				utilPerPartition.set(target, utilPerPartition.get(target) + task.util);
			} else
				return null;
		}

		for (int i = 0; i < unallocT.size(); i++) {
			int partition = unallocT.get(i).partition;
			tasks.get(partition).add(unallocT.get(i));
		}

		for (int i = 0; i < tasks.size(); i++) {
			tasks.get(i).sort((p1, p2) -> Double.compare(p1.period, p2.period));
		}

		return tasks;
	}

	private ArrayList<ArrayList<SporadicTask>> RLFS(ArrayList<SporadicTask> tasksToAllocate, ArrayList<Resource> resources, int partitions,
			double maxUtilPerCore) {
		ArrayList<Resource> resources_copy = new ArrayList<>(resources);
		resources_copy.sort((p1, p2) -> Double.compare(p1.csl, p2.csl));

		ArrayList<SporadicTask> unallocT = new ArrayList<>(tasksToAllocate);

		for (int i = 0; i < unallocT.size(); i++) {
			unallocT.get(i).partition = -1;
		}

		ArrayList<SporadicTask> sortedT = new ArrayList<>();

		for (int i = 0; i < resources_copy.size(); i++) {
			Resource res = resources_copy.get(i);
			ArrayList<SporadicTask> resT = new ArrayList<>();
			for (int j = 0; j < unallocT.size(); j++) {
				if (unallocT.get(j).resource_required_index.contains(res.id - 1)) {
					resT.add(unallocT.get(j));
					unallocT.remove(j);
					j--;
				}
			}
			resT.sort((p1, p2) -> Double.compare(p1.util, p2.util));
			sortedT.addAll(resT);
		}

		if (sortedT.size() + unallocT.size() != tasksToAllocate.size()) {
			System.err.println("resource length increase: alloc and unalloc tasks size error!");
			System.exit(-1);
		}

		ArrayList<ArrayList<SporadicTask>> tasks = NF(sortedT, partitions, maxUtilPerCore);
		if (tasks == null)
			return null;

		unallocT.sort((p1, p2) -> -Double.compare(p1.util, p2.util));

		// init util array
		ArrayList<Double> utilPerPartition = new ArrayList<>();
		for (int i = 0; i < partitions; i++) {
			Double tu1 = tasks.get(i).stream().mapToDouble(t -> t.util).sum();
			utilPerPartition.add(tu1);
		}

		for (int i = 0; i < unallocT.size(); i++) {
			SporadicTask task = unallocT.get(i);
			int target = -1;
			double minUtil = 2;
			for (int j = 0; j < partitions; j++) {
				if (minUtil > utilPerPartition.get(j)) {
					minUtil = utilPerPartition.get(j);
					target = j;
				}
			}

			if (target == -1) {
				System.err.println("WF error!");
				return null;
			}

			if ((double) 1 - minUtil >= task.util) {
				task.partition = target;
				utilPerPartition.set(target, utilPerPartition.get(target) + task.util);
			} else
				return null;
		}

		for (int i = 0; i < unallocT.size(); i++) {
			int partition = unallocT.get(i).partition;
			tasks.get(partition).add(unallocT.get(i));
		}

		for (int i = 0; i < tasks.size(); i++) {
			tasks.get(i).sort((p1, p2) -> Double.compare(p1.period, p2.period));
		}

		return tasks;
	}

}
