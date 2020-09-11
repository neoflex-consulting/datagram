import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { translate } from 'react-i18next'
import update from 'immutability-helper'
import { Row, Col, Form, Tooltip, Avatar, Button, Divider } from 'antd';
import resource from "./../../Resource";
import _ from 'lodash';
import { AgGridReact } from 'ag-grid-react';
import 'ag-grid/dist/styles/ag-grid.css';
import 'ag-grid/dist/styles/ag-theme-balham.css';

class TableView extends Component {

    static propTypes = {
        entity: PropTypes.object,
        cellEntity: PropTypes.object
    }

    constructor(...args) {
        super(...args);
        this.tableGrid = React.createRef();
        this.fieldGrid = React.createRef();
        this.state = {
            selectedTable: null,
            showTable: false,
            sessionId: null,
            queryTablesResult: [],
            queryViewsResult: [],
            queryFieldsResult: [],
            selectedRowKeys: []
        }
    }

    tableViewRefresh() {
        if (this.props.cellEntity.context) {
            const contextName = this.props.cellEntity.context ? this.props.cellEntity.context.name : undefined
            resource.query("/api/operation/MetaServer/rt/SoftwareSystem/" + contextName + "/refreshScheme").then(result => {
                resource.logInfo('Success refresh!')
                this.getTables()
            })
        } else {
            resource.logError('Context name is undefined')
        }
    }

    convertFieldType(type) {
        if (type.indexOf('DECIMAL') + 1) {
            return 'DECIMAL'
        }
        if (type.indexOf('BOOLEAN') + 1) {
            return 'BOOLEAN'
        }
        if (type.indexOf('INTEGER') + 1) {
            return 'INTEGER'
        }
        if (type.indexOf('LONG') + 1) {
            return 'LONG'
        }
        if (type === 'rel.DATE') {
            return 'DATE'
        }
        if (type.indexOf('DATETIME') + 1) {
            return 'DATETIME'
        }
        if (type.indexOf('BINARY') + 1) {
            return 'BINARY'
        }
        if (type.indexOf('FLOAT') + 1) {
            return 'FLOAT'
        }
        if (type.indexOf('DOUBLE') + 1) {
            return 'DOUBLE'
        }
        if (type.indexOf('STRUCT') + 1) {
            return 'STRUCT'
        }
        if (type.indexOf('ARRAY') + 1) {
            return 'ARRAY'
        }
        return "STRING"
    }

    tableViewApply() {
        const { cellEntity } = this.props
        const { selectedRowKeys, selectedTable } = this.state
        if (cellEntity._type_ === 'etl.TableSource') {
            const outputFields = selectedRowKeys.map(fld => ({ name: fld.field, dataTypeDomain: this.convertFieldType(fld.type), _type_: 'dataset.Field' }))
            let output = update(this.props.cellEntity.outputPort, { $merge: { fields: outputFields } })
            let full = update(this.props.cellEntity, { $merge: { outputPort: output, tableName: selectedTable[0].table } })
            this.props.updateNodeEntity(full, this.props.cellEntity)
        }
        if (cellEntity._type_ === 'etl.TableTarget') {
            const inputFields = selectedRowKeys.map(fld => ({ _type_: 'etl.TableTargetFeature', inputFieldName: fld.field, targetColumnName: fld.field }))
            this.props.updateNodeEntity(update(this.props.cellEntity, { $set: { inputFieldsMapping: inputFields, tableName: selectedTable[0].table } }), this.props.cellEntity)
        }
        if (cellEntity._type_ === 'etl.HiveTarget') {
            const inputFields = selectedRowKeys.map(fld => (
                {
                    _type_: 'etl.TableTargetFeature',
                    targetColumnName: fld.field,
                    inputFieldName: fld.field,
                    keyField: false,
                    target: {
                        e_id: cellEntity.e_id,
                        label: cellEntity.label,
                        name: cellEntity.name,
                        sampleSize: cellEntity.sampleSize,
                        _type_: cellEntity._type_
                    }
                }
            ))
            this.props.updateNodeEntity(update(this.props.cellEntity, { $set: { inputFieldsMapping: inputFields, tableName: selectedTable[0].table } }), this.props.cellEntity)
        }
    }

    getTables() {
        if (this.props.cellEntity.context) {
            const contextName = this.props.cellEntity.context.name
            resource.query("/api/teneo/select/select t.name, t.e_id from rt.SoftwareSystem ss join ss.scheme s join s.tables t where ss.name='" + contextName + "'?__orderby=name").then(
                result => {
                    this.setState({
                        queryTablesResult: result.map(tb => ({ table: tb[0], e_id: tb[1] }))
                    })
                }
            )
            resource.query("/api/teneo/select/select t.name, t.e_id from rt.SoftwareSystem ss join ss.scheme s join s.views t where ss.name='" + contextName + "'?__orderby=name").then(
                result => {
                    this.setState({
                        queryViewsResult: result.map(tb => ({ table: tb[0], e_id: tb[1] }))
                    })
                }
            )
        } else {
            resource.logError('Context name is undefined')
        }
    }

    getFields(target) {
        resource.query("/api/teneo/select/select c.name, c.e_id, type(c.dataType), cast(c.columnType as string) from rel.Table t join t.columns c where t.e_id=:e_id order by c.name?e_id=" + target[0].e_id).then(
            result => {
                this.setState({
                    queryFieldsResult: result.map(res => ({ field: res[0], type: res[2], key: res[0] }))
                })
                this.refs.fieldGrid.api.selectAll()
            }
        )
    }

    onSelectionChanged() {
        let selectedRow = this.refs.tableGrid.api.getSelectedRows()
        this.getFields(selectedRow)
    }

    componentDidMount() {
        this.getTables()
    }

    render() {
        const { t } = this.props
        const { selectedRowKeys, queryFieldsResult } = this.state
        const tables = _.concat(this.state.queryTablesResult, this.state.queryViewsResult)
        return (
            <div style={{ height: 'calc(100vh - 149px)' }}>
                <div>
                    <Form layout={"inline"}>
                        <Form.Item wrapperCol={{ span: 2, push: 14 }}>
                            <Tooltip placement="top" title={t("refreshtables")}>
                                <Button id="refreshtables" shape="circle" style={{ border: 0 }} onClick={() => {
                                    this.tableViewRefresh()
                                }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/refresh-modern.svg"} />
                                </Button>
                            </Tooltip>
                        </Form.Item>
                        {selectedRowKeys.length > 0 && <Form.Item wrapperCol={{ span: 2, push: 14 }}>
                            <Tooltip placement="top" title={t("apply")}>
                                <Button id="apply" shape="circle" style={{ border: 0 }} onClick={() => {
                                    this.tableViewApply()
                                }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/check-modern.svg"} />
                                </Button>
                            </Tooltip>
                        </Form.Item>}
                    </Form>
                    <Divider style={{ marginTop: 0, marginBottom: 0 }} />
                </div>
                <Row gutter={8}>
                    <Col span={12}>
                        <div style={{ height: '80vh', overflow: 'auto' }}>
                            <div style={{ boxSizing: "border-box", height: "100%", width: "100%" }} className="ag-theme-balham">
                                <AgGridReact
                                    ref={"tableGrid"}
                                    columnDefs={[{ headerName: 'Table', field: 'table', sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' } }]}
                                    rowData={tables}
                                    //domLayout={'autoHeight'}
                                    enableColResize={'true'}
                                    pivotHeaderHeight={'true'}
                                    enableSorting={true}
                                    sortingOrder={["desc", "asc", null]}
                                    enableFilter={true}
                                    rowSelection={'single'}
                                    onSelectionChanged={() => {
                                        this.onSelectionChanged()
                                        this.setState({ selectedTable: this.refs.tableGrid.api.getSelectedRows() })
                                    }}
                                />
                            </div>
                        </div>
                    </Col>
                    <Col span={12}>
                        <div style={{ height: '80vh', overflow: 'auto' }}>
                            <div style={{ boxSizing: "border-box", height: "100%", width: "100%" }} className="ag-theme-balham">
                                <AgGridReact
                                    ref={"fieldGrid"}
                                    columnDefs={[
                                        { headerName: 'Field', field: 'field', sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' } },
                                        { headerName: 'Type', field: 'type', sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' } }
                                    ]}
                                    rowData={queryFieldsResult}
                                    //domLayout={'autoHeight'}
                                    enableColResize={'true'}
                                    pivotHeaderHeight={'true'}
                                    enableSorting={true}
                                    sortingOrder={["desc", "asc", null]}
                                    enableFilter={true}
                                    rowSelection={'multiple'}
                                    onSelectionChanged={() => {
                                        this.setState({
                                            selectedRowKeys: this.refs.fieldGrid.api.getSelectedRows()
                                        })
                                    }}
                                />
                            </div>
                        </div>
                    </Col>
                </Row>
            </div>
        )
    }

}

export default translate()(TableView);
