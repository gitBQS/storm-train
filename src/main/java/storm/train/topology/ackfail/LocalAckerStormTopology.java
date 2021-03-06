/**
 * 
 */
package storm.train.topology.ackfail;

import java.util.Map;

import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
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
 * 测试ack/fail确认机制
 * 
 * @author buqingshuo
 * @date 2020年2月15日
 */
public class LocalAckerStormTopology {

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
			++number;
			this.collector.emit(new Values(number), number);
			System.out.println("Spout: " + number);
			// 防止数据产生太快
			Utils.sleep(1000);
		}

		@Override
		public void ack(Object msgId) {
			System.out.println("ack invoked, msgId : " + msgId);
		}

		@Override
		public void fail(Object msgId) {
			System.out.println("fail invoked, msgId : " + msgId);
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

		private OutputCollector collector;

		/**
		 * 初始化方法，只会执行一次
		 * 
		 * @param stormConf
		 * @param context
		 * @param collector
		 */
		@Override
		public void prepare(Map stormConf, TopologyContext context, OutputCollector collector) {
			this.collector = collector;
		}

		// int sum = 0;

		/**
		 * 也是一个死循环的方法，职责：获取Spout发送过来的数据并处理
		 */
		@Override
		public void execute(Tuple input) {
			// Bolt中获取值可以根据index获取，也可以根据上一个环节中定义的field的名称获取（建议使用）
			Integer num = input.getIntegerByField("num");
			// sum += num;
			// System.out.println("thread : " + Thread.currentThread().getName());
			// System.out.println("Bolt: sum = [" + sum + "]");
			// 假设num小于10为成功，大于10为失败
			if (num > 0 && num < 11) {
				this.collector.ack(input);
			} else {
				this.collector.fail(input);
			}
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
		builder.setSpout("DataSourceSpout", new DataSourceSpout());
		builder.setBolt("SumBolt", new SumBolt()).shuffleGrouping("DataSourceSpout");

		// 创建一个本地Storm集群：本地模式运行，不需要搭建Storm集群
		LocalCluster cluster = new LocalCluster();
		cluster.submitTopology("LocalAckerStormTopology", new Config(), builder.createTopology());
	}
}
