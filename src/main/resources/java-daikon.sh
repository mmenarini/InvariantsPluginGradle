#!/bin/bash

OUTPUT_FILE="@OUTPUT_FILE@"
DAIKON_JAR="@DAIKON_JAR@"
PATTERN="@PATTERN@"


function fix_string() {
  local arr=$1
  for i in "${!arr[@]}"; do
    if [[ ${arr[$i]} == *\ * ]]; then
      arr[$i]="\"${arr[$i]}\""
    fi
  done
} 

PRE=()
POST=()

if [ "$1" == '-version' ]; then
 java $@
 exit $?
fi

ISPOST=true
LAST=""

for var in "$@"
do
   if [ $ISPOST = true ]; then
      if [ ${var:0:1} != '-' ]; then
         if [ "$LAST" != "-cp" ]; then
            ISPOST=false
            POST+=("$var")
            LAST="$var"
            continue
         else
            PRE+=("${DAIKON_JAR}:"$var"")
            # PRE+=("$var")
            LAST="$var"
            continue
         fi 
      fi
      PRE+=("$var")
      LAST="$var"
   else
      POST+=("$var")
   fi
done

fix_string PRE 
fix_string POST

echo "PRE: "${PRE[@]}""
echo "POST: "${POST[@]}""

#echo "Effective command: java "${PRE[@]}" daikon.Chicory --premain=$DAIKONDIR/daikon.jar --daikon-online --daikon-args="--no_text_output -o test.inv.gz" --ppt-select-pattern="^com\.example\.getty\." "${POST[@]}""

java "${PRE[@]}" daikon.Chicory --daikon-online --daikon-args="--no_text_output -o ${OUTPUT_FILE}" --ppt-select-pattern="${PATTERN}" "${POST[@]}"
#/usr/lib/jvm/java-8-oracle/bin/java "${PRE[@]}" daikon.Chicory --daikon-online --daikon-args="--no_text_output -o ${OUTPUT_FILE}" --ppt-select-pattern="${PATTERN}" "${POST[@]}"

#/usr/lib/jvm/java-8-oracle/bin/java "${PRE[@]}" daikon.Chicory --debug --daikon-online --daikon-args="--no_text_output -o test.inv.gz" --ppt-select-pattern="^com\.example\.getty\." "${POST[@]}"
 
#java "${PRE[@]}" daikon.Chicory --premain="$DAIKONDIR/daikon.jar" --debug --daikon --daikon-args="--no_text_output -o test.inv.gz" --ppt-select-pattern="^com\.example\.getty\." "${POST[@]}"
exit $?
