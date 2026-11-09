#include <jni.h>
#include <sqlite3.h>
#include <string>
#include <cstdio>
extern "C" int sqlite3_vec_init(sqlite3*,char**,const sqlite3_api_routines*);
static void fail(JNIEnv* e,sqlite3* d,const char* fallback){e->ThrowNew(e->FindClass("java/lang/IllegalStateException"),d?sqlite3_errmsg(d):fallback);}
extern "C" JNIEXPORT jlong JNICALL Java_os_aios_core_indexing_VectorIndex_open(JNIEnv* e,jclass,jstring path){
 const char* p=e->GetStringUTFChars(path,nullptr);sqlite3* d=nullptr;int code=sqlite3_open_v2(p,&d,SQLITE_OPEN_READWRITE|SQLITE_OPEN_CREATE|SQLITE_OPEN_FULLMUTEX,nullptr);e->ReleaseStringUTFChars(path,p);
 if(code!=SQLITE_OK){fail(e,d,"Vector database unavailable");if(d)sqlite3_close(d);return 0;}sqlite3_busy_timeout(d,3000);
 if(sqlite3_vec_init(d,nullptr,nullptr)!=SQLITE_OK||sqlite3_exec(d,"PRAGMA journal_mode=WAL; CREATE VIRTUAL TABLE IF NOT EXISTS vectors USING vec0(embedding float[512]);",nullptr,nullptr,nullptr)!=SQLITE_OK){fail(e,d,"");sqlite3_close(d);return 0;}return reinterpret_cast<jlong>(d);
}
extern "C" JNIEXPORT void JNICALL Java_os_aios_core_indexing_VectorIndex_close(JNIEnv*,jclass,jlong h){sqlite3_close(reinterpret_cast<sqlite3*>(h));}
static bool erase(sqlite3* d,jlong id){sqlite3_stmt* s=nullptr;if(sqlite3_prepare_v2(d,"DELETE FROM vectors WHERE rowid=?",-1,&s,nullptr)!=SQLITE_OK)return false;sqlite3_bind_int64(s,1,id);int code=sqlite3_step(s);sqlite3_finalize(s);return code==SQLITE_DONE;}
extern "C" JNIEXPORT void JNICALL Java_os_aios_core_indexing_VectorIndex_remove(JNIEnv* e,jclass,jlong h,jlong id){auto d=reinterpret_cast<sqlite3*>(h);if(!erase(d,id))fail(e,d,"");}
extern "C" JNIEXPORT void JNICALL Java_os_aios_core_indexing_VectorIndex_put(JNIEnv* e,jclass,jlong h,jlong id,jfloatArray vector){
 auto d=reinterpret_cast<sqlite3*>(h);if(e->GetArrayLength(vector)!=512){fail(e,nullptr,"Dimension mismatch");return;}
 if(sqlite3_exec(d,"BEGIN IMMEDIATE",nullptr,nullptr,nullptr)!=SQLITE_OK){fail(e,d,"");return;}
 sqlite3_stmt* s=nullptr;bool ok=erase(d,id)&&sqlite3_prepare_v2(d,"INSERT INTO vectors(rowid,embedding) VALUES(?,?)",-1,&s,nullptr)==SQLITE_OK;
 if(ok){float data[512];e->GetFloatArrayRegion(vector,0,512,data);sqlite3_bind_int64(s,1,id);sqlite3_bind_blob(s,2,data,sizeof(data),SQLITE_TRANSIENT);ok=sqlite3_step(s)==SQLITE_DONE;}
 if(s)sqlite3_finalize(s);if(!ok){fail(e,d,"");sqlite3_exec(d,"ROLLBACK",nullptr,nullptr,nullptr);}else if(sqlite3_exec(d,"COMMIT",nullptr,nullptr,nullptr)!=SQLITE_OK)fail(e,d,"");
}
extern "C" JNIEXPORT jstring JNICALL Java_os_aios_core_indexing_VectorIndex_nearest(JNIEnv* e,jclass,jlong h,jfloatArray vector,jint count){
 auto d=reinterpret_cast<sqlite3*>(h);sqlite3_stmt* s=nullptr;if(e->GetArrayLength(vector)!=512||count<1||count>4096){fail(e,nullptr,"Invalid query");return nullptr;}
 if(sqlite3_prepare_v2(d,"SELECT rowid,distance FROM vectors WHERE embedding MATCH ? AND k=? ORDER BY distance",-1,&s,nullptr)!=SQLITE_OK){fail(e,d,"");return nullptr;}
 float data[512];e->GetFloatArrayRegion(vector,0,512,data);sqlite3_bind_blob(s,1,data,sizeof(data),SQLITE_TRANSIENT);sqlite3_bind_int(s,2,count);std::string result="[";int rc;
 while((rc=sqlite3_step(s))==SQLITE_ROW){if(result.size()>1)result+=",";char row[128];snprintf(row,sizeof(row),"{\"id\":%lld,\"distance\":%.9g}",static_cast<long long>(sqlite3_column_int64(s,0)),sqlite3_column_double(s,1));result+=row;}
 if(rc!=SQLITE_DONE){fail(e,d,"");sqlite3_finalize(s);return nullptr;}sqlite3_finalize(s);result+="]";return e->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jlong JNICALL Java_os_aios_core_indexing_VectorIndex_size(JNIEnv* e,jclass,jlong h){
 auto d=reinterpret_cast<sqlite3*>(h);sqlite3_stmt* s=nullptr;if(sqlite3_prepare_v2(d,"SELECT count(*) FROM vectors",-1,&s,nullptr)!=SQLITE_OK){fail(e,d,"");return -1;}jlong n=-1;if(sqlite3_step(s)==SQLITE_ROW)n=sqlite3_column_int64(s,0);else fail(e,d,"");sqlite3_finalize(s);return n;
}
