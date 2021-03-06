/**
 * 
 */
package storm.train.topology;

import java.util.Map;

import org.apache.storm.Config;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.AlreadyAliveException;
import org.apache.storm.generated.AuthorizationException;
import org.apache.storm.generated.InvalidTopologyException;
import org.apache.storm.spout.SpoutOutputCollector;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.topology.base.BaseRichBolt;
import org.apache.storm.topology.base.BaseRichSpout;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;
import org.apache.storm.utils.Utils;

/**
 * 使用Storm实现累加求和的操作
 * 
 * @author buqingshuo
 * @date 2020年2月15日
 */
public class ClusterSumStormExecutorsTopology {

	/**
	 * Spout需要继承BaseRichSpout
	 * 
	 * 数据源需要产生数据并发射
	 * 
	 * @author buqingshuo
	 * @date 2020年2月15日
	 */
	public static class DataSourceSpout extends BaseRichSpout {

		private SpoutOutputCollector collector;

		/**
		 * 初始化方法，只会被调用一次
		 * 
		 * @param conf      配置参数
		 * @param context   上下文
		 * @param collector 数据发射器
		 */
		@Override
		public void open(Map conf, TopologyContext context, SpoutOutputCollector collector) {
			this.collector = collector;
		}

		int number = 0;

		/**
		 * 会产生数据，在实际生产中肯定是从消息队列中获取数据
		 * 
		 * 这个方法是一个死循环，会一直不停的执行
		 */
		@Override
		public void nextTuple() {
			this.collector.emit(new Values(++number));
			System.out.println("Spout: " + number);
			// 防止数据产生太快
			Utils.sleep(1000);
		}

		/**
		 * 声明输出字段
		 */
		@Override
		public void declareOutputFields(OutputFieldsDeclarer declarer) {
			declarer.declare(new Fields("num"));
		}

	}

	/**
	 * 数据的累计求和Bolt：接受数据并处理
	 * 
	 * @author buqingshuo
	 * @date 2020年2月15日
	 */
	public static class SumBolt extends BaseRichBolt {

		/**
		 * 初始化方法，只会执行一次
		 * 
		 * @param stormConf
		 * @param context
		 * @param collector
		 */
		@Override
		public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
		}

		int sum = 0;

		/**
		 * 也是一个死循环的方法，职责：获取Spout发送过来的数据并处理
		 */
		@Override
		public void execute(Tuple input) {
			// Bolt中获取值可以根据index获取，也可以根据上一个环节中定义的field的名称获取（建议使用）
			Integer num = input.getIntegerByField("num");
			sum += num;
			System.out.println("Bolt: sum = [" + sum + "]");
		}

		@Override
		public void declareOutputFields(OutputFieldsDeclarer declarer) {

		}
	}

	public static void main(String[] args) {
		// TopologyBuilder根据Spout和Bolt来构建出Topology
		// Storm中任何一个作业都是通过Topology的方式来提交的
		// Topology中需要指定Spout和Bolt的执行顺序
		TopologyBuilder builder = new TopologyBuilder();
		// 可以设置spout、bolt的并行度，也就是executor的并行度
		builder.setSpout("DataSourceSpout", new DataSourceSpout(), 2);
		builder.setBolt("SumBolt", new SumBolt(), 2).shuffleGrouping("DataSourceSpout");

		StormSubmitter submitter = new StormSubmitter();
		String topologyName = ClusterSumStormExecutorsTopology.class.getSimpleName();
		try {
			Config config = new Config();
			config.setNumWorkers(2);
			config.setNumAckers(0);// 工作中不能设置为0，否则不能保证数据不重复发送
			submitter.submitTopology(topologyName, config, builder.createTopology());
		} catch (AlreadyAliveException | InvalidTopologyException | AuthorizationException e) {
			e.printStackTrace();
		}
	}
}
