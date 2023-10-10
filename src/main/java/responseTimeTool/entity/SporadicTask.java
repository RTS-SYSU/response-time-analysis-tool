package responseTimeTool.entity;

import java.text.DecimalFormat;
import java.util.ArrayList;

public class  SporadicTask {
	public int priority;
	public long period;//周期
	public long deadline;//截止期限
	public long C_LOW;
	public long C_HIGH;
	public int partition;//分区
	public int critical;	//关键级 0：低关键 1：高关键

	public int id;
	//for allocation
	public double util;
	public double util_LOW;
	public double util_HIGH;

	public ArrayList<Integer> resource_required_index;
	public ArrayList<Integer> number_of_access_in_one_release;	// 一次release的资源访问数量

	public long prec_LOW = 0;	// 关键性切换 LO->HI
	public long prec_HIGH = 0;	//关键性切换


	public long WCET = 0;
	public long pure_resource_execution_time = 0;	//执行资源的时间
	public long Ri = 0, spin = 0, interference = 0, local = 0, total_blocking = 0, indirect_spin = 0;	//interference 是高优先级任务的WCET+spin,indirect spin是高优先级任务的spin
	public long Ri_HI = 0, Ri_LO = 0, Ri_Switch = 0;
	public long PWLP_S= 0;


	public long spin_delay_by_preemptions = 0; //PWLP重试成本

	public double implementation_overheads = 0, blocking_overheads = 0;
	//删
	public double mrsp_arrivalblocking_overheads = 0, fifonp_arrivalblocking_overheads = 0, fifop_arrivalblocking_overheads = 0;
	public double migration_overheads_plus = 0;

	/* Used by LP solver from C code */
	public int hasResource = 0;
	public int[] resource_required_index_copy = null;
	public int[] number_of_access_in_one_release_copy = null;

	public int schedulable = -1 ;


	public SporadicTask(int priority, long t, long clo,  int partition, int id,  int critical) {
		this(priority, t, clo,  partition, id, -1, critical, 2);
	}

	public SporadicTask(int priority, long t, long clo, int partition, int id, double util_LOW, int critical, double CF) {
		this.priority = priority;
		this.period = t;
		this.C_LOW = clo;
		this.deadline = t;
		this.partition = partition;
		this.id = id;

		this.critical = critical;
		this.util_LOW = util_LOW;

		if(critical==0){
			this.C_HIGH = 0;
			this.util_HIGH = 0;
			this.util = util_LOW;
		}else{
			C_HIGH =  (long)(clo*CF);
			this.util_HIGH = util_LOW*CF;
			this.util = util_HIGH;
		}

		resource_required_index = new ArrayList<>();
		number_of_access_in_one_release = new ArrayList<>();

		Ri = 0;
		spin = 0;
		interference = 0;
		local = 0;
		Ri_HI = 0;Ri_LO = 0;Ri_Switch = 0;
	}

	@Override
	public String toString() {
		return "T" + this.id + " : T = " + this.period +
				", C_LOW = " + this.C_LOW + ", C_HIGH = " + this.C_HIGH+
				", PRET_LOW: " + this.prec_LOW + ", D = " + this.deadline
				+ ", Priority = " + this.priority + ", Partition = " + this.partition;
	}

	public String RTA() {
		return "T" + this.id + " : R = " + this.Ri + ", S = " + this.spin + ", I = " + this.interference + ", A = " + this.local + ". is schedulable: "
				+ (Ri <= deadline);
	}
	public String Ris(){
		return "Ri_Low= " + this.Ri_LO +" Ri_High = " + this.Ri_HI + " Ri_Swi= "+this.Ri_Switch+ " D: "+this.deadline;
	}

	public String getInfo() {
		DecimalFormat df = new DecimalFormat("#.#######");
		return "T" + this.id + " : T = " + this.period + ", C = " + this.C_LOW + ", PRET: " + this.pure_resource_execution_time + ", D = " + this.deadline
				+ ", Priority = " + this.priority + ", Partition = " + this.partition + ", Util: " + Double.parseDouble(df.format(util));
	}

}
