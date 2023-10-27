package com.mobigen.libs.grpc;

import com.mobigen.libs.grpc.Storage.*;

public interface StorageServiceCallBack {
    OverviewResponse overview();

    StorageTypeResponse storageType(StorageTypeRequest request);

    AdaptorResponse adaptor(AdaptorRequest request);

    InfoResponse info(InfoRequest request);

    CommonResponse connectTest(ConnectTestRequest request);
}
