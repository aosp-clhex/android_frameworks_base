/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settingslib.mobile.dataservice;

import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface MobileNetworkInfoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMobileNetworkInfo(MobileNetworkInfoEntity... mobileNetworkInfo);

    @Query("SELECT * FROM " + DataServiceUtils.MobileNetworkInfoData.TABLE_NAME + " ORDER BY "
            + DataServiceUtils.MobileNetworkInfoData.COLUMN_ID)
    LiveData<List<MobileNetworkInfoEntity>> queryAllMobileNetworkInfos();

    @Query("SELECT * FROM " + DataServiceUtils.MobileNetworkInfoData.TABLE_NAME + " WHERE "
            + DataServiceUtils.MobileNetworkInfoData.COLUMN_ID + " = :subId")
    LiveData<MobileNetworkInfoEntity> queryMobileNetworkInfoBySubId(String subId);

    @Query("SELECT * FROM " + DataServiceUtils.MobileNetworkInfoData.TABLE_NAME + " WHERE "
            + DataServiceUtils.MobileNetworkInfoData.COLUMN_IS_MOBILE_DATA_ENABLED
            + " = :isMobileDataEnabled")
    LiveData<List<MobileNetworkInfoEntity>> queryMobileNetworkInfosByMobileDataStatus(
            boolean isMobileDataEnabled);

    @Query("SELECT COUNT(*) FROM " + DataServiceUtils.MobileNetworkInfoData.TABLE_NAME)
    int count();

    @Query("DELETE FROM " + DataServiceUtils.MobileNetworkInfoData.TABLE_NAME + " WHERE "
            + DataServiceUtils.MobileNetworkInfoData.COLUMN_ID + " = :subId")
    void deleteBySubId(String subId);
}
