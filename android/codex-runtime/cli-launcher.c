#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
int main(int argc,char **argv){const char *node=getenv("AIOS_NODE"),*root=getenv("AIOS_CLI_ROOT");if(!node||!root)return 126;char path[4096];snprintf(path,sizeof(path),"%s/dispatch.cjs",root);char **args=calloc(argc+3,sizeof(char*));args[0]=(char*)node;args[1]=path;for(int i=0;i<argc;i++)args[i+2]=argv[i];execv(node,args);perror("AIOS CLI");return 126;}
