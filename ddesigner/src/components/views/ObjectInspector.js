import React, {Component} from 'react';
import {translate} from "react-i18next";
import {transformField, getModel, mergeDeep, getTypeField, getEntityClassFeature} from '../../model.js';
import {Form} from 'antd'
import FieldList from './FieldList'
import _ from 'lodash'
import resource from "../../Resource";
import update from 'immutability-helper'
import {copyField} from '../../utils/transformationPorts'

function onShemeChange(updates, value, field, entity, props) {
    if (value) {
        resource.getEntity(value._type_, value.e_id).then(valueScheme => {
            const fields = valueScheme.schemeDataset.fields.map(f => copyField(f))
            const outputPort = update(entity.outputPort, { fields: { $set: fields } })
            props.updateEntity({ outputPort })
        })
    }
}

function inContext(entity, types) {
    return entity && (
        (entity._type_ && types.includes(entity._type_)) ||
        (entity._type_ && getEntityClassFeature(oiModel, entity._type_, null, "ancestors").some(_type_ => types.includes(_type_))) ||
        inContext(entity.__parent, types)
    )
}

function onChangeName(updates, value, field, entity, props) {
    if (entity.name === entity.label) {
        updates.label = value
    }
}

let localModel = {
    eClasses: {
        "dataset.Field": {
            successors: ["dataset.Field"]
        },
        "etl.OutputPort": {
            successors: ["etl.OutputPort"],
            fields: (entity) => {
                return [
                    {
                        name: "fields",
                        type: "table",
                        entityType: inContext(entity, ["etl.XMLSource"]) ? "etl.XMLSourceField" : (
                            inContext(entity, ["etl.HBaseSource"]) ? "etl.HBaseField" : "dataset.Field"
                        ),
                        readOnly: !inContext(entity, ["etl.Source", "etl.SparkSQL", "etl.GroupWithState", "etl.Drools"])
                    },
                    {name: "debugList", type: "table", entityType: "etl.DebugOutput"},
                ]
            }
        },
        "etl.SQLSource": {
            fields: (entity) => {
                return [
                    {name: "name", type: "string"},
                    {name: "label", type: "string"},
                    {name: "sampleSize", type: "number"},
                    {name: "checkpoint", type: "boolean"},
                    {name: "contextFromString", type: "boolean"},
                    {name: "context", type: "select", entityType: "etl.Context", hidden: entity.contextFromString === true},
                    {name: "contextString", type: "string", hidden: entity.contextFromString !== true},
                    {name: "sqlOptions", type: "table", entityType: "etl.SQLOption"},
                    {name: "statement", type: "text"},
                    {name: "preSQL", type: "text"},
                    {name: "schemaOnRead", type: "boolean"},
                    {name: "isParallel", type: "boolean"},
                    {name: "partitionColumn", type: "string", hidden: entity.isParallel !== true},
                    {name: "numPartitions", type: "string", hidden: entity.isParallel !== true},
                    {name: "outputPort", type: "form", entityType: "etl.OutputPort"},
                ]
            }
        },
        "etl.TableSource": {
            fields: (entity) => {
                return [
                    {name: "name", type: "string"},
                    {name: "label", type: "string"},
                    {name: "sampleSize", type: "number"},
                    {name: "checkpoint", type: "boolean"},
                    {name: "context", type: "select", entityType: "etl.Context"},
                    {name: "tableName", type: "string"},
                    {name: "schemaOnRead", type: "boolean"},
                    {name: "outputPort", type: "form", entityType: "etl.OutputPort"},
                ]
            }
        },
        "etl.LocalSource": {
            fields: (entity) => {
                return [
                    {name: "name", type: "string"},
                    {name: "label", type: "string"},
                    {name: "sampleSize", type: "number"},
                    {name: "checkpoint", type: "boolean"},
                    {name: "localFileName", type: "string"},
                    {
                        name: "localFileFormat",
                        type: "enum",
                        options: getTypeField("etl.LocalSource", "localFileFormat").options
                    },
                    {name: "formatName", type: "string", hidden: entity.localFileFormat !== "OTHER"},
                    {name: "schemaOnRead", type: "boolean"},
                    {name: "streaming", type: "boolean"},
                    {name: "options", type: "table", entityType: "etl.SourceOption"},
                    {name: "outputPort", type: "form", entityType: "etl.OutputPort"},
                ]
            }
        },
        "etl.ExpressionSource": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "checkpoint", type: "boolean"},
                {name: "expression", type: "text"},
                {name: "outputPort", type: "form", entityType: "etl.OutputPort"},
            ]
        },
        "etl.CSVSource": {
            fields: (entity) => {
                return [
                    {name: "name", type: "string"},
                    {name: "label", type: "string"},
                    {name: "sampleSize", type: "number"},
                    {name: "checkpoint", type: "boolean"},
                    {name: "path", type: "string"},
                    {
                        name: "csvFormat", type: "enum",
                        options: getTypeField("etl.CSVSource", "csvFormat").options
                    },
                    {name: "header", type: "boolean"},
                    {
                        name: "CSV", type: "set", fields: [
                            {name: "charset", type: "string"},
                            {name: "delimiter", type: "string"},
                            {name: "quote", type: "string"},
                            {name: "escape", type: "string"},
                            {name: "comment", type: "string"},
                            {name: "dateFormat", type: "string"},
                            {name: "nullValue", type: "string"},
                        ],
                        hidden: !["CSV"].includes(entity.csvFormat)
                    },
                    {
                        name: "EXCEL", type: "set", fields: [
                            {name: "dataAddress", type: "string"},
                            {name: "addColorColumns", type: "string"},
                            {name: "treatEmptyValuesAsNulls", type: "boolean"},
                            {name: "timestampFormat", type: "string"},
                            {name: "maxRowsInMemory", type: "number"},
                        ],
                        hidden: !["EXCEL"].includes(entity.csvFormat)
                    },
                    {name: "outputPort", type: "form", entityType: "etl.OutputPort"},
                ]
            }
        },
        "etl.XMLSource": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "sampleSize", type: "number"},
                {name: "checkpoint", type: "boolean"},
                {name: "path", type: "string"},
                {name: "charset", type: "string"},
                {
                    name: "XML", type: "set", fields: [
                        {name: "rowTag", type: "string"},
                        {name: "samplingRatio", type: "number"},
                        {name: "excludeAttribute", type: "boolean"},
                        {name: "treatEmptyValuesAsNulls", type: "boolean"},
                        {name: "mode", type: "enum", options: getTypeField("etl.XMLSource", "mode").options},
                        {name: "columnNameOfCorruptRecord", type: "string"},
                        {name: "attributePrefix", type: "string"},
                        {name: "valueTag", type: "string"},
                        {name: "ignoreSurroundingSpaces", type: "boolean"},
                    ]
                },
                {name: "explodeFields", type: "table", entityType: "etl.ExplodeField"},
                {name: "schemaOnRead", type: "boolean"},
                {name: "outputPort", type: "form", entityType: "etl.OutputPort"},
            ]
        },
        "etl.AVROSource": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "sampleSize", type: "number"},
                {name: "checkpoint", type: "boolean"},
                {name: "path", type: "string"},
                {name: "schemaHdfs", type: "boolean"},
                {name: "schemaPath", type: "string"},
                {name: "charset", type: "string"},
                {name: "explodeFields", type: "table", entityType: "etl.AvroExplodeField"},
                {name: "schemaOnRead", type: "boolean"},
                {name: "outputPort", type: "form", entityType: "etl.OutputPort"},
            ]
        },
        "etl.HiveSource": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "sampleSize", type: "number"},
                {name: "checkpoint", type: "boolean"},
                {name: "context", type: "select", entityType: "etl.Context"},
                {name: "explain", type: "boolean"},
                {name: "statement", type: "text"},
                {name: "schemaOnRead", type: "boolean"},
                {name: "outputPort", type: "form", entityType: "etl.OutputPort"},
            ]
        },
        "etl.HBaseSource": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "checkpoint", type: "boolean"},
                {name: "namespace", type: "string"},
                {name: "tableName", type: "string"},
                {name: "rowkey", type: "string"},
                {name: "minStamp", type: "string"},
                {name: "maxStamp", type: "string"},
                {name: "maxVersions", type: "string"},
                {name: "mergeToLatest", type: "boolean"},
                {name: "outputPort", type: "form", entityType: "etl.OutputPort"},
            ]
        },
        "etl.KafkaSource": {
            fields: (entity) => {
                return [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "checkpoint", type: "boolean"},
                {name: "bootstrapServers", type: "string"},
                {
                    name: "kafkaConsumeType",
                    type: "enum",
                    options: getTypeField("etl.KafkaSource", "kafkaConsumeType").options
                },
                {name: "consumeOptionValue", type: "string"},
                {name: "options", type: "table", entityType: "etl.KafkaSourceOption"},
                {name: "valueType", type: "enum", options: getTypeField("etl.KafkaSource", "valueType").options},
                {name: "valueScheme", type: "select", entityType: "etl.SchemeDataSet", onChange: onShemeChange},
                {name: "outputPort", type: "form", entityType: "etl.OutputPort", readOnly: entity.valueScheme ? true : false
                }
            ]}
        },
        "etl.Join": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "checkpoint", type: "boolean"},
                {name: "joinType", type: "enum", options: getTypeField("etl.Join", "joinType").options},
                {name: "keyFields", type: "multiString", listeval: "props.entity.inputPort.fields"},
                {name: "joineeKeyFields", type: "multiString", listeval: "props.entity.joineePort.fields"},
                {name: "watermarkField", type: "selectString", listeval: "props.entity.outputPort.fields"},
                {name: "watermarkThreshold", type: "string"},
                {name: "outputPort", type: "form", entityType: "etl.OutputPort"},
            ]
        },
        "etl.AggregationParameter": {
            fields: [
                {name: "resultFieldName", type: "string"},
                {name: "fieldName", type: "selectString", listeval: "props.entity.__parent.inputPort.fields"},
                {
                    name: "aggregationFunction",
                    type: "enum",
                    options: getTypeField("etl.AggregationParameter", "aggregationFunction").options
                },
            ]
        },
        "etl.Aggregation": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "checkpoint", type: "boolean"},
                {name: "groupByFieldName", type: "multiString", listeval: "props.entity.inputPort.fields"},
                {name: "aggregationParameters", type: "table", entityType: "etl.AggregationParameter"},
                {name: "pivotField", type: "selectString", listeval: "props.entity.inputPort.fields"},
                {name: "pivotParameters", type: "table", entityType: "etl.PivotParameter"},
                {name: "userDefAgg", type: "boolean"},
                {
                    name: "Expressions", type: "set", fields: [
                        {name: "expression", type: "text"},
                        {name: "initExpression", type: "text"},
                        {name: "finalExpression", type: "text"},
                        {name: "mergeExpression", type: "text"},
                    ]
                },
                {name: "outputPort", type: "form", entityType: "etl.OutputPort"},
            ]
        },
        "etl.SortFeature": {
            fields: [
                {name: "fieldName", type: "selectString", listeval: "props.entity.__parent.inputPort.fields"},
                {name: "ascending", type: "boolean"},
            ]
        },
        "etl.Sort": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "checkpoint", type: "boolean"},
                {name: "sortFeatures", type: "table", entityType: "etl.SortFeature"},
                {name: "outputPort", type: "form", entityType: "etl.OutputPort"},
            ]
        },
        "etl.Selection": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "checkpoint", type: "boolean"},
                {name: "expression", type: "text"},
                {name: "outputPort", type: "form", entityType: "etl.OutputPort"},
            ]
        },
        "etl.Projection": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "checkpoint", type: "boolean"},
                {name: "watermarkField", type: "selectString", listeval: "props.entity.outputPort.fields"},
                {name: "watermarkThreshold", type: "string"},
                {name: "outputPort", type: "form", entityType: "etl.OutputPort"},
            ]
        },
        "etl.Sequence": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "checkpoint", type: "boolean"},
                {name: "fieldName", type: "string"},
                {name: "sequenceType", type: "enum", options: getTypeField("etl.Sequence", "sequenceType").options},
                {name: "context", type: "select", entityType: "etl.Context"},
                {name: "sequencedName", type: "string"},
                {name: "batchSize", type: "number"},
                {name: "outputPort", type: "form", entityType: "etl.OutputPort"},
            ]
        },
        "etl.Union": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "checkpoint", type: "boolean"},
                {name: "outputPort", type: "form", entityType: "etl.OutputPort"},
            ]
        },
        "etl.SparkSQL": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "checkpoint", type: "boolean"},
                {name: "explain", type: "boolean"},
                {name: "customSQL", type: "boolean"},
                {name: "sampleSize", type: "number"},
                {name: "schemaOnRead", type: "boolean"},
                {name: "statement", type: "text"},
                {name: "sqlPorts", type: "table", entityType: "etl.SQLPort"},
                {name: "outputPort", type: "form", entityType: "etl.OutputPort"},
            ]
        },
        "etl.ExplodeStep": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "checkpoint", type: "boolean"},
                {name: "explodeFields", type: "table", entityType: "etl.ExplodeStepField"},
                {name: "outputPort", type: "form", entityType: "etl.OutputPort"},
            ]
        },
        "etl.Drools": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "checkpoint", type: "boolean"},
                {name: "rulesFiles", type: "table", entityType: "etl.DroolsRulesFile"},
                {name: "globals", type: "table", entityType: "etl.Property"},
                {name: "inputFactTypeName", type: "string"},
                {name: "resultFactTypeName", type: "string"},
                {name: "resultQueryName", type: "string"},
                {name: "resultFactName", type: "string"},
                {name: "outputPort", type: "form", entityType: "etl.OutputPort"},
            ]
        },
        "etl.ModelBasedAnalysis": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "checkpoint", type: "boolean"},
                {name: "modelFile", type: "string"},
                {name: "labelFieldName", type: "string"},
                {name: "modelFeaturesFields", type: "multiString", listeval: "props.entity.inputPort.fields"},
                {
                    name: "methodName",
                    type: "enum",
                    options: getTypeField("etl.ModelBasedAnalysis", "methodName").options
                },
                {name: "outputPort", type: "form", entityType: "etl.OutputPort"},
            ]
        },
        "etl.TableTargetFeature": {
            fields: [
                {name: "inputFieldName", type: "selectString", listeval: "props.entity.__parent.inputPort.fields"},
                {name: "targetColumnName", type: "string"},
                {name: "keyField", type: "boolean"},
            ]
        },
        "etl.TableTarget": {
            fields: (entity) => {
                return [
                    {name: "name", type: "string"},
                    {name: "label", type: "string"},
                    {name: "context", type: "select", entityType: "etl.Context"},
                    {name: "tableName", type: "string"},
                    {name: "targetType", type: "enum", options: getTypeField("etl.TableTarget", "targetType").options},
                    {name: "schemaOnRead", type: "boolean"},
                    {name: "clear", type: "boolean"},
                    {name: "checkIfExists", type: "boolean"},
                    {name: "preSQL", type: "text"},
                    {name: "postSQL", type: "text"},
                    {name: "coalesceFromString", type: "boolean"},
                    {name: "coalesce", type: "number", hidden: entity.coalesceFromString === true},
                    {name: "coalesceString", type: "string", hidden: entity.coalesceFromString !== true},
                    {name: "repartitionNumFromString", type: "boolean"},
                    {name: "repartitionNum", type: "number", hidden: entity.repartitionNumFromString === true},
                    {name: "repartitionNumString", type: "string", hidden: entity.repartitionNumFromString !== true},
                    {name: "repartitionExpression", type: "string"},
                    {name: "inputFieldsMapping", type: "table", entityType: "etl.TableTargetFeature"},
                ]
            }
        },
        "etl.StoredProcedureParamFeature": {
            fields: [
                {name: "inputFieldName", type: "selectString", listeval: "props.entity.__parent.inputPort.fields"},
                {name: "paramName", type: "string"},
            ]
        },
        "etl.StoredProcedureTarget": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "context", type: "select", entityType: "etl.Context"},
                {name: "catalogName", type: "string"},
                {name: "storedProcedure", type: "string"},
                {name: "preSQL", type: "text"},
                {name: "postSQL", type: "text"},
                {name: "inputFieldsMapping", type: "table", entityType: "etl.StoredProcedureParamFeature"},
            ]
        },
        "etl.LocalTarget": {
            fields: (entity) => {
                return [
                    {name: "name", type: "string"},
                    {name: "label", type: "string"},
                    {
                        name: "localFileFormat",
                        type: "enum",
                        options: getTypeField("etl.LocalTarget", "localFileFormat").options
                    },
                    {name: "formatName", type: "string", hidden: entity.localFileFormat !== "OTHER"},
                    {name: "saveMode", type: "enum", options: getTypeField("etl.LocalTarget", "saveMode").options},
                    {name: "localFileName", type: "string"},
                    {name: "deleteBeforeSave", type: "boolean"},
                    {name: "registerTable", type: "boolean"},
                    {name: "coalesceFromString", type: "boolean"},
                    {name: "coalesce", type: "number", hidden: entity.coalesceFromString === true},
                    {name: "coalesceString", type: "string", hidden: entity.coalesceFromString !== true},
                    {name: "repartitionNumFromString", type: "boolean"},
                    {name: "repartitionNum", type: "number", hidden: entity.repartitionNumFromString === true},
                    {name: "repartitionNumString", type: "string", hidden: entity.repartitionNumFromString !== true},
                    {name: "repartitionExpression", type: "string"},
                    {name: "hiveTableName", type: "string"},
                    {name: "options", type: "table", entityType: "etl.TargetOption"},
                    {name: "partitionsFromString", type: "boolean"},
                    {name: "partitions", type: "multiString", listeval: "props.entity.inputPort.fields", hidden: entity.partitionsFromString === true},
                    {name: "partitionsString", type: "string", hidden: entity.partitionsFromString !== true},
                    {name: "inputFieldsMapping", type: "table", entityType: "etl.TableTargetFeature"},
                ]
            }
        },
        "etl.StreamTarget": {
            fields: (entity) => {
                return [
                    {name: "name", type: "string"},
                    {name: "label", type: "string"},
                    {name: "context", type: "select", entityType: "etl.Context"},
                    {name: "outputMode", type: "enum", options: getTypeField("etl.StreamTarget", "outputMode").options},
                    {name: "checkpointLocation", type: "string"},
                    {
                        name: "localFileFormat",
                        type: "enum",
                        options: getTypeField("etl.StreamTarget", "localFileFormat").options
                    },
                    {name: "formatName", type: "string", hidden: entity.localFileFormat !== "OTHER"},
                    {name: "localFileName", type: "string"},
                    {name: "trigger", type: "number"},
                    {
                        name: "triggerUnits",
                        type: "enum",
                        options: getTypeField("etl.StreamTarget", "triggerUnits").options
                    },
                    {name: "timeoutMs", type: "number"},
                    {name: "refreshTimeoutMs", type: "number"},
                    {name: "options", type: "table", entityType: "etl.TargetOption"},
                    {name: "partitions", type: "multiString", listeval: "props.entity.inputPort.fields"},
                    {name: "namespace", type: "string", hidden: !["HBASE"].includes(entity.localFileFormat)},
                    {name: "tableName", type: "string", hidden: !["HBASE"].includes(entity.localFileFormat)},
                    {name: "rowkey", type: "string", hidden: !["HBASE"].includes(entity.localFileFormat)},
                    {name: "newTable", type: "number", hidden: !["HBASE"].includes(entity.localFileFormat)},
                    {
                        name: "versionColumn",
                        type: "selectString",
                        hidden: !["HBASE"].includes(entity.localFileFormat),
                        listeval: "props.entity.inputPort.fields"
                    },
                    {
                        name: "inputFieldsMapping",
                        type: "table",
                        entityType: "etl.HBaseTargetFeature",
                        hidden: !["HBASE"].includes(entity.localFileFormat)
                    },
                ]
            },
        },
        "etl.CSVTarget": {
            fields: (entity) => {
                return [
                    {name: "name", type: "string"},
                    {name: "label", type: "string"},
                    {name: "sampleSize", type: "number"},
                    {name: "path", type: "string"},
                    {
                        name: "csvFormat", type: "enum",
                        options: getTypeField("etl.CSVTarget", "csvFormat").options
                    },
                    {name: "header", type: "boolean"},
                    {
                        name: "CSV", type: "set", fields: [
                            {name: "charset", type: "string"},
                            {name: "delimiter", type: "string"},
                            {name: "quote", type: "string"},
                            {name: "escape", type: "string"},
                            {name: "comment", type: "string"},
                            {name: "dateFormat", type: "string"},
                            {name: "nullValue", type: "string"},
                            {name: "codec", type: "enum", options: getTypeField("etl.CSVTarget", "codec").options},
                            {
                                name: "quoteMode",
                                type: "enum",
                                options: getTypeField("etl.CSVTarget", "quoteMode").options
                            },
                        ],
                        hidden: !["CSV"].includes(entity.csvFormat)
                    },
                    {
                        name: "EXCEL", type: "set", fields: [
                            {
                                name: "saveMode", type: "enum",
                                options: getTypeField("etl.CSVTarget", "saveMode").options
                            },
                            {name: "dataAddress", type: "string"},
                            {name: "dateFormat", type: "string"},
                            {name: "timestampFormat", type: "string"},
                        ],
                        hidden: !["EXCEL"].includes(entity.csvFormat)
                    },
                ]
            }
        },
        "etl.HiveTarget": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "context", type: "select", entityType: "etl.Context"},
                {name: "tableName", type: "string"},
                {name: "clear", type: "boolean"},
                {
                    name: "hiveTargetType",
                    type: "enum",
                    options: getTypeField("etl.HiveTarget", "hiveTargetType").options
                },
                {name: "preSQL", type: "text"},
                {name: "postSQL", type: "text"},
                {name: "partitions", type: "multiString", listeval: "props.entity.inputPort.fields"},
                {name: "inputFieldsMapping", type: "table", entityType: "etl.TableTargetFeature"},
            ]
        },
        "etl.HBaseTarget": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "namespace", type: "string"},
                {name: "tableName", type: "string"},
                {name: "rowkey", type: "string"},
                {name: "newTable", type: "number"},
                {
                    name: "versionColumn",
                    type: "selectString",
                    listeval: "props.entity.inputPort.fields"
                },
                {name: "inputFieldsMapping", type: "table", entityType: "etl.HBaseTargetFeature"},
            ]
        },
        "etl.HBaseTargetFeature": {
            fields: [
                {name: "inputFieldName", type: "selectString", listeval: "props.entity.__parent.inputPort.fields"},
                {name: "family", type: "string"},
                {name: "column", type: "string"},
            ]
        },
        "etl.KafkaTarget": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "bootstrapServers", type: "string"},
                {name: "topicName", type: "string"},
                {name: "messageKey", type: "selectString", listeval: "props.entity.inputPort.fields"},
                {name: "messageValue", type: "selectString", listeval: "props.entity.inputPort.fields"},
                {name: "props", type: "table", entityType: "etl.KafkaTargetProperty"},
                {name: "valueType", type: "enum", options: getTypeField("etl.KafkaTarget", "valueType").options},
                {name: "valueScheme", type: "select", entityType: "etl.SchemeDataSet"},
            ]
        },
        "etl.XMLTarget": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "path", type: "string"},
                {name: "charset", type: "string"},
                {
                    name: "XML", type: "set", fields: [
                        {name: "rowTag", type: "string"},
                        {name: "rootTag", type: "string"},
                        {name: "nullValue", type: "string"},
                        {name: "attributePrefix", type: "string"},
                        {name: "valueTag", type: "string"},
                        {
                            name: "compression",
                            type: "enum",
                            options: getTypeField("etl.XMLTarget", "compression").options
                        },
                    ]
                },
            ]
        },
        "etl.WFManualStart": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "to", type: "select", listeval: "props.entity.__parent.nodes"},
            ]
        },
        "etl.WFTransformation": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "transformation", type: "select", entityType: "etl.Transformation"},
                {name: "jvmOpts", type: "string"},
                {name: "prepare", type: "form", entityType: "etl.Prepare"},
                {name: "parameters", type: "table", entityType: "etl.Property"},
                {name: "sla", type: "form", entityType: "etl.SlaDefinition"},
                {name: "ok", type: "select", listeval: "props.entity.__parent.nodes"},
                {name: "error", type: "select", listeval: "props.entity.__parent.nodes"},
            ]
        },
        "etl.WFSubWorkflow": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "subWorkflow", type: "select", entityType: "etl.Workflow"},
                {name: "propagateConfiguration", type: "boolean"},
                {name: "properties", type: "table", entityType: "etl.ConfigurationProperty"},
                {name: "sla", type: "form", entityType: "etl.SlaDefinition"},
                {name: "ok", type: "select", listeval: "props.entity.__parent.nodes"},
                {name: "error", type: "select", listeval: "props.entity.__parent.nodes"},
            ]
        },
        "etl.WFShell": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "exec", type: "string"},
                {name: "args", type: "stringList"},
                {name: "file", type: "string"},
                {name: "captureOutput", type: "boolean"},
                {name: "sla", type: "form", entityType: "etl.SlaDefinition"},
                {name: "ok", type: "select", listeval: "props.entity.__parent.nodes"},
                {name: "error", type: "select", listeval: "props.entity.__parent.nodes"},
            ]
        },
        "etl.WFJava": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "jarFiles", type: "stringList"},
                {name: "prepare", type: "form", entityType: "etl.Prepare"},
                {name: "mainclass", type: "string"},
                {name: "args", type: "stringList"},
                {name: "javaopts", type: "string"},
                {name: "captureOutput", type: "boolean"},
                {name: "file", type: "string"},
                {name: "archive", type: "string"},
                {name: "properties", type: "table", entityType: "etl.ConfigurationProperty"},
                {name: "sla", type: "form", entityType: "etl.SlaDefinition"},
                {name: "ok", type: "select", listeval: "props.entity.__parent.nodes"},
                {name: "error", type: "select", listeval: "props.entity.__parent.nodes"},
            ]
        },
        "etl.WFFork": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "paths", type: "multi", listeval: "props.entity.__parent.nodes"},
            ]
        },
        "etl.WFJoin": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "to", type: "select", listeval: "props.entity.__parent.nodes"},
            ]
        },
        "etl.WFCase": {
            fields: [
                {name: "label", type: "string"},
                {name: "predicate", type: "string"},
                {name: "to", type: "select", listeval: "props.entity.__parent.__parent.nodes"},
            ]
        },
        "etl.WFDecision": {
            fields: [
                {name: "name", type: "string"},
                {name: "label", type: "string"},
                {name: "cases", type: "table", entityType: "etl.WFCase"},
                {name: "default", type: "select", listeval: "props.entity.__parent.nodes"},
            ]
        },
    }
}
localModel = mapPathValues(localModel, "eClasses", "*", "fields", (value) => {
    return !_.isArray(value) ? value : value.map(f => {
        const field = transformField(f)
        return field.name !== "name" ? field : {...field, onChange: onChangeName}
    })
});
let oiModel = mergeDeep({}, getModel(), localModel);

function mapPathValues(target, ...paths) {
    if (!paths.length) return target;
    let path = paths.shift();
    if (_.isFunction(path)) {
        return mapPathValues(path(target), ...paths)
    }
    return _.mapValues(target, (value, key) => {
        if (key === path || path === "*") {
            return mapPathValues(value, ...paths)
        }
        return value
    })
}

class ObjectInspector extends Component {

    constructor(...args) {
        super(...args);
        this.state = {fields: []}
    }

    propsChanged(props) {
        let fields = [];
        if (!!props.entity && !!props.entity._type_) {
            fields = this.getEntityClassFeature(null, props.entity, "fields")
        }
        this.setState({fields})
    }

    componentDidMount() {
        this.propsChanged(this.props)
    }

    componentWillReceiveProps(nextProps) {
        this.propsChanged(nextProps)
    }

    getEntityClassFeature(typeName, entity, feature) {
        return getEntityClassFeature(oiModel, typeName, entity, feature)
    }

    render() {
        return (
            <Form onSubmit={e => e.preventDefault()} layout={"vertical"}>
                <FieldList fields={this.state.fields}
                           getEntityClassFeature={(typeName, entity, feature) => this.getEntityClassFeature(typeName, entity, feature)} {...this.props}/>
            </Form>)
    }
}

export default translate()(ObjectInspector);
