#!/usr/bin/env node

/**
 * 抖音热榜定时任务脚本
 * 用于 OpenClaw cron 自动化
 */

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

// 获取热榜数据
function getHotTrend(limit = 10) {
  try {
    const scriptPath = path.join(__dirname, 'douyin.js');
    const output = execSync(`node "${scriptPath}" hot ${limit}`, {
      encoding: 'utf-8',
      cwd: path.dirname(scriptPath)
    });
    return output;
  } catch (error) {
    console.error('获取抖音热榜失败:', error.message);
    return null;
  }
}

// 格式化热榜消息
function formatMessage(hotTrendText) {
  const lines = hotTrendText.split('\n').filter(line => line.trim());
  
  let message = '🔥 **抖音热榜 TOP 10**\n';
  message += '=' .repeat(50) + '\n\n';
  
  let inList = false;
  let itemCount = 0;
  
  for (const line of lines) {
    // 跳过标题和分隔线
    if (line.includes('抖音热榜') || line.includes('===') || line.includes('正在获取')) {
      continue;
    }
    
    // 解析榜单条目
    if (line.match(/^\s*\d+\./)) {
      inList = true;
      itemCount++;
      if (itemCount > 10) break; // 只取前10
      
      // 提取排名和标题
      const match = line.match(/^\s*(\d+)\.\s*(.+)/);
      if (match) {
        const rank = match[1];
        const title = match[2].trim();
        message += `${rank}. ${title}\n`;
      }
    } else if (inList && line.includes('热度:')) {
      // 提取热度值
      const hotMatch = line.match(/热度:\s*([\d,]+)/);
      if (hotMatch) {
        message += `   🔥 ${hotMatch[1]}\n\n`;
      }
    }
  }
  
  message += '\n📱 数据来源：抖音网页端公开接口';
  message += '\n⏰ 更新时间：' + new Date().toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai' });
  
  return message;
}

// 主函数
async function main() {
  const limit = process.argv[2] || 10;
  
  console.log('开始获取抖音热榜...');
  const hotTrendData = getHotTrend(limit);
  
  if (!hotTrendData) {
    console.error('❌ 获取热榜数据失败');
    process.exit(1);
  }
  
  const message = formatMessage(hotTrendData);
  
  // 输出到文件（用于调试）
  const outputFile = path.join(__dirname, 'latest-hot-trend.txt');
  fs.writeFileSync(outputFile, message, 'utf-8');
  
  // 输出到 stdout（OpenClaw 会捕获）
  console.log('\n=== 抖音热榜报告 ===\n');
  console.log(message);
  
  // 保存为 JSON 格式（供程序调用）
  const jsonOutput = {
    success: true,
    timestamp: new Date().toISOString(),
    timezone: 'Asia/Shanghai',
    message: message,
    rawData: hotTrendData
  };
  
  const jsonFile = path.join(__dirname, 'latest-hot-trend.json');
  fs.writeFileSync(jsonFile, JSON.stringify(jsonOutput, null, 2), 'utf-8');
  
  console.log('\n✅ 热榜数据已保存到:');
  console.log(`  - ${outputFile}`);
  console.log(`  - ${jsonFile}`);
}

main().catch(error => {
  console.error('执行出错:', error);
  process.exit(1);
});
