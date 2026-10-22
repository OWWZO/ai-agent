package org.wwz.ai.test;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class ApiTest {

    @Test
    public void test() {
        // 分隔线，提升输出可读性
        String separator = "================================================";

        // 1. 模拟苹果质量分类（逻辑回归+朴素贝叶斯）运行结果
        System.out.println("【实验1：苹果质量二分类任务】");
        System.out.println(separator);
        // 数据探索输出
        System.out.println("1. 数据探索结果：");
        System.out.println("   - 数据集行数/列数：4001/8");
        System.out.println("   - 缺失值数量：所有列均为0");
        System.out.println("   - 目标变量分布：good(2306)、bad(1695)");
        // 逻辑回归评估指标
        System.out.println("\n2. 逻辑回归模型评估：");
        System.out.println("   - 平均绝对误差(MAE)：0.245");
        System.out.println("   - R²得分：0.521");
        System.out.println("   - 测试集准确率：0.783");
        System.out.println("   - 分类报告（good类）：精确率0.79，召回率0.81，F1值0.80");
        // 朴素贝叶斯评估指标
        System.out.println("\n3. 朴素贝叶斯模型评估：");
        System.out.println("   - 平均绝对误差(MAE)：0.312");
        System.out.println("   - R²得分：0.415");
        System.out.println("   - 测试集准确率：0.718");

        // 2. 模拟保险费用预测（套索回归+岭回归）运行结果
        System.out.println("\n\n【实验2：保险费用回归预测任务】");
        System.out.println(separator);
        System.out.println("1. 数据预处理结果：");
        System.out.println("   - 类别特征(sex/smoker/region)已完成独热编码");
        System.out.println("   - 数值特征(age/bmi/children)已标准化");
        System.out.println("\n2. 套索回归(Lasso, alpha=47)评估：");
        System.out.println("   - 测试集R²得分：0.792");
        System.out.println("   - 示例预测：真实费用12500.5元 → 预测费用11890.3元");
        System.out.println("\n3. 岭回归(Ridge, alpha=0.95)评估：");
        System.out.println("   - 测试集R²得分：0.847");
        System.out.println("   - 示例预测：真实费用38900.2元 → 预测费用37560.8元");

        // 3. 模拟手机价格区间分类（决策树+随机森林）运行结果
        System.out.println("\n\n【实验3：手机价格区间多分类任务】");
        System.out.println(separator);
        System.out.println("1. 数据集基本信息：");
        System.out.println("   - 维度：2000行 × 21列（20个特征+1个目标变量）");
        System.out.println("   - 目标变量(price_range)：0/1/2/3（4个价格区间），各500条");
        System.out.println("\n2. 决策树模型评估：");
        System.out.println("   - 训练集准确率：1.0（过拟合）");
        System.out.println("   - 测试集R²得分：0.678");
        System.out.println("\n3. 随机森林模型评估(n_estimators=150)：");
        System.out.println("   - 训练集准确率：0.995");
        System.out.println("   - 测试集准确率：0.912");
        System.out.println("   - 分类报告（3类-高价机）：精确率0.94，召回率0.93，F1值0.935");

        // 4. 模拟关键结论输出
        System.out.println("\n\n【实验总结】");
        System.out.println(separator);
        System.out.println("1. 分类任务中：随机森林 > 逻辑回归 > 朴素贝叶斯 > 决策树（测试集效果）");
        System.out.println("2. 回归任务中：岭回归 > 套索回归 > 线性回归（保险/收入预测场景）");
        System.out.println("3. 正则化模型（岭/套索/弹性网络）有效降低过拟合，提升泛化能力");
    }

}
