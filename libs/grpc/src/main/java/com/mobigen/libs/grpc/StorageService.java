package com.mobigen.libs.grpc;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class StorageService extends StorageServiceGrpc.StorageServiceImplBase{

    StorageServiceCallBack callBack;

    public StorageService(StorageServiceCallBack callBack) {
        log.debug("Init Storage Service");
        this.callBack = callBack;
    }

    @Override
    public void overview( Empty request, StreamObserver<OverviewResponse> responseObserver ) {

        log.debug(">> Storage:Overview");
        responseObserver.onNext( callBack.overview() );
        log.debug("<< Storage:Overview");
        responseObserver.onCompleted();
    }
}