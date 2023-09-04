package responseTimeTool.entity;

import java.util.ArrayList;

public class Resource {

	public int id;
	public long csl;  // 临界资源长度
	public long csl_low;  //临界资源下限
	public long csl_high;  //临界资源上限

	public ArrayList<SporadicTask> requested_tasks;	//请求该资源的任务列表

	public ArrayList<Integer> partitions;	//请求该资源的分区列表
	public ArrayList<Integer> ceiling;	// 资源的静态ceiling


	public boolean isGlobal = false;

	public Resource(int id, long cs_len) {
		this.id = id;
		this.csl = cs_len;
		this.csl_low = cs_len;
		this.csl_high = cs_len * 2;
		requested_tasks = new ArrayList<>();
		partitions = new ArrayList<>();
		ceiling = new ArrayList<>();
	}

	@Override
	public String toString() {
		return "R" + this.id + " : cs len = " + this.csl_low + ", partitions: " + partitions.size() + ", tasks: " + requested_tasks.size() + ", isGlobal: "
				+ isGlobal;
	}

	public int getCeilingForProcessor(ArrayList<ArrayList<SporadicTask>> tasks, int partition) {
		int ceiling = -1;

		for (int k = 0; k < tasks.get(partition).size(); k++) {
			SporadicTask task = tasks.get(partition).get(k);

			if (task.resource_required_index.contains(this.id - 1)) {
				ceiling = task.priority > ceiling ? task.priority : ceiling;
			}
		}

		return ceiling;
	}

	public int getCeilingForProcessor(ArrayList<SporadicTask> tasks) {
		int ceiling = -1;

		for (int k = 0; k < tasks.size(); k++) {
			SporadicTask task = tasks.get(k);

			if (task.resource_required_index.contains(this.id - 1)) {
				ceiling = task.priority > ceiling ? task.priority : ceiling;
			}
		}

		return ceiling;
	}
}
