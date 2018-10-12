package com.veelur.sync.elasticsearch.service.impl;

import com.veelur.sync.elasticsearch.common.BaseConstants;
import com.veelur.sync.elasticsearch.common.MainTypeEnum;
import com.veelur.sync.elasticsearch.exception.InfoNotRightException;
import com.veelur.sync.elasticsearch.service.DadaMappingService;
import com.veelur.sync.elasticsearch.util.DateUtils;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Maps;
import com.veelur.sync.elasticsearch.model.*;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * @author: veelur
 * @date: 18-9-21
 * @Description: {相关描述}
 */
@Service
@ConfigurationProperties(prefix = "dada.db-es")
public class DadaMappingServiceImpl implements DadaMappingService, InitializingBean {

    private List<DadaConvertModel> mappings = new ArrayList<>();


    public List<DadaConvertModel> getMappings() {
        return mappings;
    }

    public void setMappings(List<DadaConvertModel> mappings) {
        this.mappings = mappings;
    }

    private BiMap<DadaDatabaseModel, DadaIndexTypeModel> dbEsBiMapping;
    private Map<String, DadaMappingServiceImpl.Converter> mysqlTypeElasticsearchTypeMapping;
    private BiMap<DataDatabaseTableModel, DadaConnectModel> dbSingleMapping;

    @Override
    public DadaDatabaseModel getDatabaseWithIndexType(String index, String type) {
        return dbEsBiMapping.inverse().get(new DadaIndexTypeModel(index, type));
    }

    @Override
    public DadaConnectModel getColumnWithData(String database, String table) {
        return dbSingleMapping.get(new DataDatabaseTableModel(database, table));
    }

    @Override
    public Object getElasticsearchTypeObject(String mysqlType, String data) {
        Optional<Map.Entry<String, DadaMappingServiceImpl.Converter>> result = mysqlTypeElasticsearchTypeMapping.entrySet()
                .parallelStream().filter(entry -> mysqlType.toLowerCase().contains(entry.getKey())).findFirst();
        return (result.isPresent() ? result.get().getValue() : (DadaMappingServiceImpl.Converter) data1 -> data1).convert(data);
    }

    @Override
    public void afterPropertiesSet() throws InfoNotRightException {
        dbEsBiMapping = HashBiMap.create();
        if (CollectionUtils.isEmpty(mappings)) {
            throw new InfoNotRightException("mapping映射为空");
        }
        DadaDatabaseModel dadaDatabaseModel;
        DadaIndexTypeModel dadaIndexTypeModel;
        List<DataDatabaseTableModel> models;
        DataDatabaseTableModel tableModel;
        DadaConnectModel dadaConnectModel;
        String include;
        String exclude;
        String[] split;
        dbSingleMapping = HashBiMap.create();

        for (DadaConvertModel model : mappings) {
            boolean havaMain = false;
            dadaIndexTypeModel = new DadaIndexTypeModel();
            dadaIndexTypeModel.setIndex(model.getIndex());
            dadaIndexTypeModel.setType(model.getType());
            //获取数据库
            dadaDatabaseModel = new DadaDatabaseModel();
            List<DadaDbConvertModel> dbs = model.getDbs();
            models = new ArrayList<>();
            for (DadaDbConvertModel dbConvertModel : dbs) {
                tableModel = new DataDatabaseTableModel();
                tableModel.setDatabase(dbConvertModel.getDatabase());
                tableModel.setTable(dbConvertModel.getTable());
                tableModel.setMain(null != dbConvertModel.getMain() ? dbConvertModel.getMain() : MainTypeEnum.MAIN.getCode());
                if (havaMain && MainTypeEnum.MAIN.getCode().equals(tableModel.getMain())) {
                    throw new InfoNotRightException("包含重复的mapping-main信息");
                }
                String listkv = dbConvertModel.getListkv();
                if (MainTypeEnum.ONE_TO_MORE.getCode().equals(tableModel.getMain())) {
                    if (StringUtils.isNotEmpty(listkv)) {
                        String[] split1 = listkv.split(BaseConstants.DEFAULT_SPLIT_2);
                        tableModel.setListname(split1[0]);
                        tableModel.setMainKey(split1[1]);
                    } else {
                        tableModel.setListname(tableModel.getTable());
                        tableModel.setMainKey(BaseConstants.DEFAULT_ID);
                    }
                }
                tableModel.setPkStr(StringUtils.isEmpty(dbConvertModel.getPkstr()) ? BaseConstants.DEFAULT_ID : dbConvertModel.getPkstr());
                include = dbConvertModel.getInclude();
                if (StringUtils.isNotEmpty(include)) {
                    split = include.split(BaseConstants.DEFAULT_SPLIT);
                    tableModel.setIncludeField(Arrays.asList(split));
                }
                exclude = dbConvertModel.getExclude();
                if (StringUtils.isNotEmpty(exclude)) {
                    split = exclude.split(BaseConstants.DEFAULT_SPLIT);
                    tableModel.setExcludeField(Arrays.asList(split));
                }
                tableModel.setConvert(dbConvertModel.getConvert());
                models.add(tableModel);
                if (MainTypeEnum.MAIN.getCode().equals(tableModel.getMain())) {
                    havaMain = true;
                    if (StringUtils.isEmpty(dadaIndexTypeModel.getIndex())) {
                        dadaIndexTypeModel.setIndex(tableModel.getDatabase());
                    }
                    if (StringUtils.isEmpty(dadaIndexTypeModel.getType())) {
                        dadaIndexTypeModel.setIndex(tableModel.getTable());
                    }
                }
                dadaConnectModel = new DadaConnectModel();
                dadaConnectModel.setDbModel(tableModel);
                dadaConnectModel.setEsModel(dadaIndexTypeModel);
                dbSingleMapping.put(tableModel, dadaConnectModel);
            }
            dadaDatabaseModel.setModels(models);
            dbEsBiMapping.put(dadaDatabaseModel, dadaIndexTypeModel);
        }
        mysqlTypeElasticsearchTypeMapping = Maps.newHashMap();
        mysqlTypeElasticsearchTypeMapping.put("char", data -> data);
        mysqlTypeElasticsearchTypeMapping.put("text", data -> data);
        mysqlTypeElasticsearchTypeMapping.put("blob", data -> data);
        mysqlTypeElasticsearchTypeMapping.put("int", Long::valueOf);
        mysqlTypeElasticsearchTypeMapping.put("date", DateUtils::convertDate);
        mysqlTypeElasticsearchTypeMapping.put("time", DateUtils::convertDate);
        mysqlTypeElasticsearchTypeMapping.put("float", Double::valueOf);
        mysqlTypeElasticsearchTypeMapping.put("double", Double::valueOf);
        mysqlTypeElasticsearchTypeMapping.put("decimal", Double::valueOf);
    }


    @FunctionalInterface
    private interface Converter {
        Object convert(String data);
    }
}