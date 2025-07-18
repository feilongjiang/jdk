#!/bin/bash
LIMIT_HANDLE_NUM=128 # size of block_size_in_oops, see jniHandles.hpp
for ((i=0; i<$LIMIT_HANDLE_NUM;i++)); do
    offset=$(($i*8))
    echo "offset: $offset"
    # run test command here
    ./build/linux-aarch64-server-release/images/jdk/bin/java -XX:-TieredCompilation -XX:CICompilerCount=1  -XX:CompileCommand=customhandle,java.lang.ref.Finalizer::register -XX:JNIHandleBlockAllocOffset=$offset Test

    if [ $? -ne 134 ]; then
        echo "Test pased at iter: $i"
    else
        echo "Test stopped at iter: $i, allocate offset: $offset"
        exit 1
    fi
done