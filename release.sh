#!/bin/bash

# 该脚本用于打包某目录(可以是绝对路径)下的指定扩展名的文件

if [ $# -lt 1 ] || [ "$1" = "." ] || [ "$1" = "./" ]; then
  DIR=$(pwd)
else
  DIR=$1
  if [ ! -e $DIR ]; then
    echo "Directory-[$DIR] not exist, exit now!"
    exit
  fi
fi

packageName=$(basename $DIR)-$(date +%F-%H-%M).tar.gz # 压缩包文件名

if [ -e "$packageName" ]; then
  mv -f $packageName "$packageName".bak # 备份原有的压缩包
fi

packSrcs() { # 查找相关文件并打包
  DIR=$1
  packageName=$2
  pattern=$3

  IFS='|' read -a array <<<"$patten" # 将pattern以"|"作分割, 将结果存入array

  n=${#array[@]}
  index=1

  # 接下来组合查找字符并打包的字符串
  findstr="find $DIR -name '*${array[0]}'"
  while test $index -lt $n; do
    findstr="$findstr -o -name '*${array[index]}'"
    let "index = index + 1"
  done

  mkdir plugins-release
  dosh="$findstr | xargs -i cp {} plugins-release/"
  dotar="tar -zcvf $packageName plugins-release/*"

  echo $dosh
  eval $dosh # 将字符串当命令执行
  eval $dotar
  rm -r plugins-release
}

patten=".eag"                       # 要打包的文件的扩展名
#patten=".lua|.sh|.bat"

packSrcs $DIR $packageName $pattern # 调用打包函数
