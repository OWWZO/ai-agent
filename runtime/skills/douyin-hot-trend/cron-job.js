#!/usr/bin/env node

/**
 * 抖音热榜定时任务 - OpenClaw 集成版本
 * 直接发送到 Telegram
 */

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

// 获取热榜数据
function getHotTrend(limit = 10) {
  try {
    const scriptPath = path.join(__dirname, 'scripts', 'douyin.js');
    const output = execSync(`node "${scriptPath}" hot ${limit}`, {
      encoding: 'utf-8',
      cwd: __dirname
    });
    return output;
  } catch (error) {
    console.error('获取抖音热榜失败:', error.message);
    return null;
  }
}

// 解析热榜数据
function parseHotTrend(output) {
  const lines = output.split('\n');
  const items = [];
  
  let currentItem = null;
  
  for (const line of lines) {
    // 匹配排名行
    const rankMatch = line.match(/^\s*(\d+)\.\s+(.+)/);
    if (rankMatch) {
      if (currentItem && currentItem.title) {
        items.push(currentItem);
      }
      currentItem = {
        rank: parseInt(rankMatch[1]),
        title: rankMatch[2].trim(),
        popularity: 0,
        link: '',
        label: null
      };
      continue;
    }
    
    // 匹配热度值
    if (currentItem && line.includes('热度:')) {
      const hotMatch = line.match(/热度:\s*([\d,]+)/);
      if (hotMatch) {
        currentItem.popularity = parseInt(hotMatch[1].replace(/,/g, ''));
      }
    }
    
    // 匹配链接
    if (currentItem && line.includes('链接:')) {
      const linkMatch = line.match(/链接:\s*(.+)/);
      if (linkMatch) {
        currentItem.link = linkMatch[1].trim();
      }
    }
    
    // 匹配标签
    if (currentItem && line.includes('标签:')) {
      const labelMatch = line.match(/标签:\s*(.+)/);
      if (labelMatch) {
        currentItem.label = labelMatch[1].trim();
      }
    }
  }
  
  if (currentItem && currentItem.title) {
    items.push(currentItem);
  }
  
  return items;
}

// 格式化 Telegram 消息
function formatTelegramMessage(items) {
  let message = '🔥 **抖音热榜 TOP ' + items.length + '**\n';
  message += '_每天下午6点自动推送_\n\n';
  
  for (const item of items) {
    const emoji = ['🥇', '🥈', '🥉'][item.rank - 1] || '🎯';
    message += `${emoji} *${item.rank}.* ${item.title}\n`;
    message += `   🔥 ${item.popularity.toLocaleString()}\n`;
    
    if (item.rank < 5) {
      // 前5名显示链接
      message += `   [查看详情](${item.link})\n`;
    }
    
    message += '\n';
  }
  
  message += '📱 _数据来源：抖音网页端_';
  message += '\n⏰ ' + new Date().toLocaleString('zh-CN', { 
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  });
  
  return message;
}

// 主函数
async function main() {
  const limit = 10;
  
  console.log('🎬 开始执行抖音热榜定时任务...');
  
  // 获取热榜数据
  const rawData = getHotTrend(limit);
  if (!rawData) {
    console.error('❌ 获取热榜数据失败');
    process.exit(1);
  }
  
  // 解析数据
  const items = parseHotTrend(rawData);
  console.log(`✅ 成功解析 ${items.length} 条热榜数据`);
  
  // 格式化消息
  const message = formatTelegramMessage(items);
  
  // 保存到文件（调试用）
  const outputFile = path.join(__dirname, 'daily-hot-trend-message.txt');
  fs.writeFileSync(outputFile, message, 'utf-8');
  
  // 输出 JSON（供 OpenClaw 消息工具使用）
  const jsonOutput = {
    success: true,
    timestamp: new Date().toISOString(),
    timezone: 'Asia/Shanghai',
    chat_id: '8428610733',
    channel: 'telegram',
    message: message,
    items: items.slice(0, limit),
    format: 'markdown'
  };
  
  const jsonFile = path.join(__dirname, 'daily-hot-trend-output.json');
  fs.writeFileSync(jsonFile, JSON.stringify(jsonOutput, null, 2), 'utf-8');
  
  console.log('📤 消息已准备发送到 Telegram');
  console.log(`💾 保存位置: ${jsonFile}`);
  
  // 输出消息内容（OpenClaw 会捕获）
  console.log('\n=== 消息预览 ===\n');
  console.log(message);
  
  return jsonOutput;
}

main().catch(error => {
  console.error('执行出错:', error);
  process.exit(1);
});
