#!/usr/bin/env node
'use strict';

var fs = require('fs');
var path = require('path');

var root = path.resolve(__dirname,
  '../web/src/main/resources/static');
var extensions = new Set([
  '.html', '.js', '.css', '.json', '.webmanifest', '.svg'
]);
var errors = [];
var checked = 0;

function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap(function (entry) {
    var target = path.join(dir, entry.name);
    return entry.isDirectory() ? walk(target) : [target];
  });
}

function cleanReference(value) {
  return value.trim().replace(/[?#].*$/, '');
}

function isExternal(value) {
  return !value || value[0] === '#' ||
    /^(?:https?:|wss?:|data:|blob:|mailto:|tel:|javascript:)/i.test(value) ||
    /^\/(?:api|ws)\//.test(value) ||
    value.indexOf('{{') >= 0 ||
    /[\s+()]/.test(value);
}

function checkReference(source, rawValue) {
  var value = cleanReference(rawValue);
  if (isExternal(value) || value === '/') return;
  var target = value[0] === '/'
    ? path.join(root, value)
    : path.resolve(path.dirname(source), value);
  checked++;
  if (!fs.existsSync(target)) {
    errors.push(path.relative(root, source) + ' -> ' + rawValue);
  }
}

walk(root).forEach(function (file) {
  if (!extensions.has(path.extname(file))) return;
  var content = fs.readFileSync(file, 'utf8');
  var patterns = [
    /appUrl\(\s*["']([^"']+)["']\s*\)/g
  ];
  if (path.extname(file) === '.js') {
    patterns.push(
      /(?:^|\n)\s*(?:import|export)\s+(?:[^"'()]*?\s+from\s+)?["']([^"']+)["']/g,
      /import\(\s*["']([^"']+)["']\s*\)/g,
      /new\s+Worker\(\s*["']([^"']+)["']/g
    );
  }
  if (path.extname(file) === '.html') {
    patterns.push(/(?<![.:\w@])(?:src|href)\s*=\s*["']([^"']+)["']/gi);
  }
  if (path.extname(file) === '.css') {
    patterns.push(/url\(\s*["']?([^"')]+)["']?\s*\)/gi);
  }
  patterns.forEach(function (pattern) {
    var match;
    while ((match = pattern.exec(content))) checkReference(file, match[1]);
  });
});

if (errors.length) {
  process.stderr.write('发现静态资源断链：\n' + errors.join('\n') + '\n');
  process.exit(1);
}
process.stdout.write('Web 静态路径检查通过：已核对 ' + checked + ' 个引用。\n');
