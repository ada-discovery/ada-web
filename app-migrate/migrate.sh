#!/bin/bash

echo "    ______         __           
   /      \       |  \          
  |  ######\  ____| ##  ______  
  | ##__| ## /      ## |      \ 
  | ##    ##|  #######  \######\\
  | ########| ##  | ## /      ##
  | ##  | ##| ##__| ##|  #######
  | ##  | ## \##    ## \##    ##
   \##   \##  \#######  \#######
---------------------------------
      MIGRATION ASSISTANT
---------------------------------
                                  "
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
bold=$(tput bold)
normal=$(tput sgr0)

CUR_DIR=$SCRIPT_DIR/../
APP_CONF_FILE=$CUR_DIR/conf/application.conf

if [ ! -f $APP_CONF_FILE ];
then
    echo -e "\n>>> Something is wrong. This script was not run from an Ada installation dir.\n"	
    exit 1
fi

APP_VERSION_LINE=$(grep -F "app.version" $APP_CONF_FILE)
APP_VERSION=${APP_VERSION_LINE#*= }

echo -e "Current Ada version is ${bold}$APP_VERSION${normal}.\n"

if [[ $APP_VERSION < "0.7.0" ]];
then
    echo -e ">>> This script can be used only for Ada versions 0.7.0 and above.\n"	
    exit 1
fi

PREV_DIR_CORRECT=""
while [[ $PREV_DIR_CORRECT != "y" && $PREV_DIR_CORRECT != "Y" ]];
do
  # PREV_DIR="USER INPUT"
  read -p "Enter the (root) dir of your previous Ada installation: " PREV_DIR

  if [ -d $PREV_DIR ];
  then
    PREV_APP_CONF_FILE=$PREV_DIR/conf/application.conf
    if [ -f $PREV_APP_CONF_FILE ];
    then
      PREV_APP_VERSION_LINE=$(grep -F "app.version" $PREV_APP_CONF_FILE)
      PREV_APP_VERSION=${PREV_APP_VERSION_LINE#*= }
      echo -e "\n>>> Found the app version ${bold}$PREV_APP_VERSION${normal} in the dir '$PREV_DIR'.\n"
      if [[ $APP_VERSION > $PREV_APP_VERSION ]];
      then	
        # PREV_DIR_CORRECT="USER INPUT"
        read -p "Is it correct [y/n]? " PREV_DIR_CORRECT
      else
        echo -e ">>> Cannot continue. Your (current) version must be newer than the source one.\n"
      fi 
    else
      echo -e "\n>>> Cannot find an application config. The dir '$PREV_DIR' does not seem to belong to an Ada instalation.\n"
    fi
  else
    echo -e "\n>>> The dir '$PREV_DIR' does not exist.\n"
  fi
done

# Configuration files

echo -e "\n>>> Copying configuration files...\n"

cp $PREV_DIR/conf/custom.conf $CUR_DIR/conf/custom.conf
cp $PREV_DIR/bin/set_env.sh $CUR_DIR/bin/set_env.sh

PREV_TEMP_LINE=$(grep -F "ADA_TEMP=" $PREV_DIR/bin/runme)
PREV_MEM_LINE=$(grep -F "ADA_MEM=" $PREV_DIR/bin/runme)

sed "s|ADA_TEMP=.*|${PREV_TEMP_LINE}|g" $CUR_DIR/bin/runme > $CUR_DIR/bin/runme_x 
sed "s|ADA_MEM=.*|${PREV_MEM_LINE}|g" $CUR_DIR/bin/runme_x > $CUR_DIR/bin/runme

rm $CUR_DIR/bin/runme_x 

# Copy extra folders, e.g. dataImports, and images

cd $PREV_DIR

echo -e ">>> Searching for extra sub dirs to copy..."

for i in $(ls -d */);
do 
  SUB_DIR=${i%%/};
  if [[ $SUB_DIR != "bin" && $SUB_DIR != "conf" && $SUB_DIR != "lib" && $SUB_DIR != "share" ]];
  then
    echo -e "\nCopying '$SUB_DIR'..."
    cp -r $PREV_DIR/$SUB_DIR $CUR_DIR/$SUB_DIR
  fi
done

# Mongo

echo -e "\n>>> ${bold}Warning: DB migration has to be done manually. Please execute all the db-update scripts from > $PREV_APP_VERSION to $APP_VERSION${normal}.\n"

echo -e ">>> Ada migration has been successfully finished!\n"

echo "---------------------------------"
echo -e "\n[[EnJoY Ada Discovery Analytics. Visit us at https://ada-discovery.org. Bye]]\n"
