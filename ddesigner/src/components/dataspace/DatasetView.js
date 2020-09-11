import React, { Component } from 'react';
import { translate } from "react-i18next";
import { Modal, Divider, Form, Button, Tooltip, InputNumber, Avatar, Radio, Icon, Input } from 'antd';
import _ from 'lodash';
import resource from "./../../Resource";
import Graph from './Graph';
import NfDataGrid from '../NfDataGrid';
import update from 'immutability-helper'
import RowEditForm from './RowEditForm'

class DatasetView extends Component {
    constructor(...args) {
        super(...args);
        this.state = {
            result: null,
            batchSize: 100,
            view: "table",
            filter: ""
        }
        this.DataGrid = React.createRef();
    }

    recordsEquals(r1, r2) {
        return _.get(this.props.entity, "primaryKeyCols", []).findIndex(key => r1[key] !== r2[key]) < 0
    }

    getData() {
        const entity = this.props.entity
        if (entity.name) {
            resource.call(entity, "fetchData", {
                batchSize: this.state.batchSize,
                filter: this.state.filter
            }).then(result => {
                if (result.status === 'OK') {
                    const colNames = result.columns.map((c) => _.last(c.columnName.split('.')))
                    const rows = (result.rows || []).map(r => _.zipObject(colNames, r))
                    this.setState({ result, rows })
                } else {
                    resource.logError(`${result.status} \n ${result.problems.map(p => p).join('')}`)
                }
            })
        }
    }

    createTable() {
        const result = this.state.result
        if (result.columns) {
            const columns = result.columns.map((col) => ({
                headerName: _.last(col.columnName.split('.')),
                field: _.last(col.columnName.split('.')),
                sortingOrder: ["asc", "desc"]
            }))
            return (
                <div style={{ boxSizing: 'border-box', height: '100%', width: '100%' }}>
                    {this.state.view === "table" && <NfDataGrid
                        ref={this.DataGrid}
                        columnDefs={columns}
                        rowData={this.state.rows}
                        gridOptions={{
                            enableColResize: true,
                            pivotHeaderHeight: true,
                            enableSorting: true,
                            sortingOrder: ["desc", "asc", null],
                            enableFilter: true,
                            gridAutoHeight: true,
                            rowSelection: 'single',
                            rowMultiSelectWithClick: true,
                            onCellClicked: () => {
                                this.forceUpdate()
                            }
                        }}
                    />}
                    <Graph
                        visible={this.state.view !== "table"}
                        view={this.state.view}
                        data={this.state.rows}
                        columns={_.sortBy(result.columns, "columnName")}
                    />
                </div>
            )
        }
    }

    selectRow(row) {
        const nodes = []
        this.getApi().forEachNode(node => {
            if (this.recordsEquals(row, node.data)) {
                nodes.push(node)
            }
        })
        if (nodes.length > 0) {
            nodes[0].setSelected(true, true)
        }
    }

    updateOrInsertRow(row) {
        const index = this.state.rows.findIndex(r => this.recordsEquals(r, row))
        if (index >= 0) {
            const rows = update(this.state.rows, { $splice: [[index, 1, row]] })
            this.setState({ rows })
        }
        else {
            const rows = update(this.state.rows, { $push: [row] })
            this.setState({ rows }, () => this.selectRow(row))
        }
    }

    getCSV() {
        this.DataGrid.current.exportToCSV(this.props.entity.shortName)
    }

    getApi() {
        return _.get(this, ['DataGrid', 'current', 'grid', 'current', 'api'])
    }

    isSelected() {
        const api = this.getApi()
        return api && api.getSelectedNodes().length > 0
    }

    isEditable() {
        return this.props.entity._type_ === "sse.ReferenceDataset" &&
            _.get(this.props.entity, "primaryKeyCols", []).length > 0 &&
            this.state.view === "table"
    }

    componentDidMount() {
        const { activeObject, entityPromise } = this.props
        entityPromise.then(() => {
            if (!["sse.Dataset", "sse.QueryDataset"].includes(activeObject._type_)) {
                this.getData()
            }
        })
    }

    getCurrentRow() {
        const columns = this.props.entity.columns || []
        const emptyRow = _.zipObject(columns.map(c => c.columnName), _.fill(Array(columns.length), ''))
        const selectedRow = this.isSelected() ? this.getApi().getSelectedRows()[0] : {}
        return Object.assign(emptyRow, _.pickBy(selectedRow, (value, key) => !!value))
    }

    render() {
        const { t, entity } = this.props
        const { result, batchSize } = this.state
        return (
            <div style={{ height: 'calc(100vh - 152px)' }}>
                {this.state.modal && <Modal title={t(this.state.modal)}
                    visible={true}
                    cancelText={t('cancel')}
                    okText={t("save")}
                    onCancel={() => {
                        this.setState({ modal: undefined })
                    }}
                    onOk={() => {
                        resource.callByName(this.props.entity, "upsert", this.state.currentRow).then(resp => {
                            if (resp.status === "OK") {
                                this.setState({ modal: undefined }, () => this.updateOrInsertRow(this.state.currentRow))
                            }
                        })
                    }}
                >
                    <RowEditForm
                        ds={entity}
                        row={this.state.currentRow}
                        updateRow={r => this.setState({ currentRow: Object.assign(this.state.currentRow, r) })}
                        op={this.state.modal}
                    />
                </Modal>}
                <Form layout={"inline"} style={{ marginLeft: 20 }}>
                    <Form.Item>
                        <Tooltip placement="top" title={t("show")}>
                            <Button shape="circle" style={{ border: 0 }} onClick={() => {
                                this.getData()
                            }}><Avatar className="avatar-button-tool-panel"
                                src="images/icon-core/show-modern.svg" /></Button>
                        </Tooltip>
                    </Form.Item>
                    {result && <Form.Item>
                        <Tooltip placement="top" title={t("exportascsv")}>
                            <Button shape="circle" style={{ border: 0 }} onClick={() => {
                                this.getCSV()
                            }}><Avatar className="avatar-button-tool-panel" src="images/icon-core/csv.svg" /></Button>
                        </Tooltip>
                    </Form.Item>}
                    {result && this.isEditable() && <Form.Item>
                        <Tooltip placement="top" title={t("new")}>
                            <Button shape="circle" style={{ border: 0 }} onClick={() => {
                                this.setState({ modal: "new", currentRow: this.getCurrentRow() })
                            }}><Avatar className="avatar-button-tool-panel" src="images/icon-core/sql-add.svg" /></Button>
                        </Tooltip>
                    </Form.Item>}
                    {result && this.isEditable() && <Form.Item>
                        <Tooltip placement="top" title={t("edit")}>
                            <Button shape="circle" style={{ border: 0 }} disabled={!this.isSelected()} onClick={() => {
                                this.setState({ modal: "edit", currentRow: this.getCurrentRow() })
                            }}><Avatar className="avatar-button-tool-panel" src="images/icon-core/sql-update.svg" /></Button>
                        </Tooltip>
                    </Form.Item>}
                    {result && this.isEditable() && <Form.Item>
                        <Tooltip placement="top" title={t("delete")}>
                            <Button shape="circle" style={{ border: 0 }} disabled={!this.isSelected()} onClick={() => {
                                Modal.confirm({
                                    content: t("confirmdelete"),
                                    okText: t("delete"),
                                    cancelText: t("cancel"),
                                    onOk: () => {
                                        const this_ = this
                                        const node = this.getApi().getSelectedNodes()[0]
                                        resource.callByName(this.props.entity, "delete", node.data).then(resp => {
                                            if (resp.status === "OK") {
                                                const index = this_.state.rows.findIndex(e => {
                                                    return this.recordsEquals(e, node.data)
                                                })
                                                if (index >= 0) {
                                                    const rows = update(this_.state.rows, { $splice: [[index, 1]] })
                                                    this.setState({ rows })
                                                }
                                            }
                                        })
                                    }
                                })
                            }}><Avatar className="avatar-button-tool-panel" src="images/icon-core/sql-delete.svg" /></Button>
                        </Tooltip>
                    </Form.Item>}
                    <Form.Item>
                        <Tooltip placement="top" key="samplesize" title={t('samplesize')}>
                            <InputNumber size="small" value={batchSize}
                                onChange={(value) => this.setState({ batchSize: value })} />
                        </Tooltip>
                    </Form.Item>
                    <Form.Item>
                        <Radio.Group value={this.state.view} onChange={e => this.setState({ view: e.target.value })}>
                            <Radio.Button className="radio-button-custom" size="small" value="table"
                                style={{ border: 0, boxShadow: "none" }}><Tooltip title={"Table"}><Icon
                                    type="table" /></Tooltip></Radio.Button>
                            <Radio.Button className="radio-button-custom" size="small" value="line-chart"
                                style={{ border: 0, boxShadow: "none" }}><Tooltip title={"Line Chart"}><Icon
                                    type="line-chart" /></Tooltip></Radio.Button>
                            <Radio.Button className="radio-button-custom" size="small" value="bar-chart"
                                style={{ border: 0, boxShadow: "none" }}><Tooltip title={"Bar Chart"}><Icon
                                    type="bar-chart" /></Tooltip></Radio.Button>
                        </Radio.Group>
                    </Form.Item>
                    <Form.Item>
                        <Tooltip placement="top" title={t('sse.Workspace.views.filter', { ns: ['classes'] })}>
                            <Input id="filter"
                                style={{ width: '60vh' }}
                                size="small" placeholder={t('sse.Workspace.views.filter', { ns: ['classes'] })}
                                onChange={(e) => this.setState({ filter: e.target.value })}
                                onKeyUp={e => {
                                    if (e.keyCode === 13) {
                                        this.getData()
                                    }
                                }}
                            />
                        </Tooltip>
                    </Form.Item>
                </Form>
                <Divider style={{ marginTop: 0, marginBottom: 0 }} />
                <div style={{ height: 'calc(100vh - 189px)', overflow: 'auto' }}>
                    {result && this.createTable()}
                </div>
            </div>
        )
    }
}

export default translate()(DatasetView);
