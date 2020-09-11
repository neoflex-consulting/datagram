import React, { Component, Fragment } from 'react';
import { translate } from "react-i18next";
import { Avatar, Divider, Tooltip, Form, Button, Modal, Row, Col, Select } from 'antd';
import resource from "../../Resource";
import _ from 'lodash';
import ImportDatasetView from './ImportDatasetView';
import NfDataGrid from '../NfDataGrid';
//import update from 'immutability-helper'

class WorkspaceGridView extends Component {
    constructor(...args) {
        super(...args);
        this.state = {
            workspaceDatasets: [],
            workspaces: [],
            importVisible: false,
            createLinkVisible: false,
            mapShouldUpdate: false,
            selectedDataset: null,
            workspaceForLink: null,
            selectedRow: null
        }
        this.DataGrid = React.createRef();
    }

    getWorkspaceDatasetList() {
        const { entity } = this.props
        if (entity.e_id) {
            resource.query("/api/teneo/select/select type(e),e.e_id,e.shortName,e.description, case d when NULL then '' else type(d) end,d.e_id,d.shortName from sse.AbstractDataset e left join e.datasets d where e.workspace.e_id=" + entity.e_id).then(result => {
                if (result) {
                    let datasetList = []
                    const arrayWithKeys = result.map(e => ({ _type_: e[0], e_id: e[1], shortName: e[2], description: e[3], p_type_: e[4], pe_id: e[5], pshortName: e[6] }))
                    const groupedDatasets = _.groupBy(arrayWithKeys, 'shortName')

                    for (let ds in groupedDatasets) {
                        if (groupedDatasets.hasOwnProperty(ds)) {
                            groupedDatasets[ds].forEach(el => {
                                if (datasetList.find(d => d.shortName === el.shortName)) {
                                    el.pshortName !== null && datasetList.find(d => d.shortName === el.shortName).datasets.push({ _type_: el.p_type_, e_id: el.pe_id, shortName: el.pshortName, description: el.description })
                                } else {
                                    datasetList.push({
                                        _type_: el._type_,
                                        e_id: el.e_id,
                                        shortName: el.shortName,
                                        description: el.description,
                                        datasets: el.pshortName !== null ? [{ _type_: el.p_type_, e_id: el.pe_id, shortName: el.pshortName }] : undefined
                                    })
                                }
                            })
                        }
                    }
                    this.setState({ workspaceDatasets: datasetList })
                }
            })
        }
    }

    getPlainDatasetList(datasetList) {
        let groupedDatasets = []
        datasetList.forEach(d => {
            groupedDatasets.push(d)
            d.datasets && d.datasets.forEach(child => {
                groupedDatasets.push(child)
            })
        })
        return groupedDatasets
    }

    handlePanelClick(key, shortName) {
        const { workspaceDatasets } = this.state
        const { entity, context, selectObject, t } = this.props
        const groupedDatasets = this.getPlainDatasetList(workspaceDatasets)
        const dataset = groupedDatasets.find(d => d.shortName === shortName)
        if (key === "createQuery") {
            const parent = groupedDatasets.find(d => d.shortName === shortName)
            const workspace_eid = entity.e_id
            const newActiveObject = { _type_: "sse.Dataset", name: "", parent_dataset: parent.e_id, workspace_eid: workspace_eid }
            selectObject(newActiveObject)
        }
        if (key === "viewData") {
            context.updateContext({ activeComponent: "DatasetView" })
            selectObject({ _type_: dataset._type_, name: dataset.shortName, e_id: dataset.e_id }, { args: { activeView: "DatasetView" } })
        }
        if (key === "createLink") {
            this.setState({ createLinkVisible: true, selectedDataset: dataset })
        }
        if (key === "deleteDataset") {
            Modal.confirm({
                content: t("confirmdelete"),
                okText: t("delete"),
                cancelText: t("cancel"),
                onOk: () => {
                    resource.deleteEntity({ _type_: dataset._type_, name: dataset.shortName, e_id: dataset.e_id }).then(entity => {
                        this.setRefresh(true)
                    })
                }
            })
        }
    }

    handleNodeClick(e, shortName) {
        const { workspaceDatasets } = this.state
        const groupedDatasets = this.getPlainDatasetList(workspaceDatasets)
        const dataset = groupedDatasets.find(d => d.shortName === shortName)
        this.props.selectObject({ _type_: dataset._type_, name: dataset.shortName, e_id: dataset.e_id })
    }

    setVisible(visible) {
        this.setState({ importVisible: visible })
    }

    setRefresh(value) {
        const { activeObject } = this.props
        this.props.selectObject({ ...activeObject }, { replace: true })
        this.setState({ mapShouldUpdate: value })
    }

    linkDataset = () => {
        const { workspaceForLink, selectedDataset } = this.state
        const entity = {
            shortName: selectedDataset.shortName,
            name: `${workspaceForLink.name}_${selectedDataset.shortName}`,
            _type_: "sse.LinkedDataset",
            linkTo: selectedDataset,
            workspace: workspaceForLink
        }
        resource.saveEntity(entity)
        this.setState({ createLinkVisible: false })
    }

    handleCancel = () => {
        this.setState({ createLinkVisible: false })
    }

    createDisplayList() {
        const { workspaceDatasets } = this.state
        const columns = [
            { headerName: 'Dataset', field: 'dataset', sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' }, width: 200 },
            { headerName: 'Type', field: '_type_', sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' }, width: 200 },
            { headerName: 'Description', field: 'description', sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' }, width: 400 }
        ]
        const rows = workspaceDatasets.length > 0 && this.getPlainDatasetList(workspaceDatasets).map(d => ({ dataset: d.shortName, _type_: d._type_, description: d.description }))
        return (
            <NfDataGrid
                ref={this.DataGrid}
                columnDefs={columns}
                rowData={rows}
                gridOptions={{
                    rowSelection: 'single',
                    rowMultiSelectWithClick: true,
                    onCellClicked: this.cellClick
                }}
            />
        )
    }

    cellClick = (a) => {
        if (a.data) {
            a.node.selected ? this.setState({ selectedRow: a.data }) : this.setState({ selectedRow: null })
        }
    }

    componentDidUpdate() {
        if (this.state.mapShouldUpdate === true) {
            this.getWorkspaceDatasetList()
            this.setRefresh(false)
        }
    }

    componentDidMount() {
        this.getWorkspaceDatasetList()
        resource.getSimpleSelect('sse.Workspace', ['name']).then(list => {
            this.setState({ workspaces: list })
        })
    }

    render() {
        const { t } = this.props
        const { workspaceDatasets, importVisible, selectedRow, createLinkVisible, workspaces } = this.state

        return (
            workspaceDatasets.length > 0 &&
            <div style={{ height: 'calc(100vh - 152px)' }}>
                <Form layout={"inline"}>
                    <Row type="flex">
                        <Col>
                            <Form.Item>
                                <Tooltip placement="top" title={t("sse.Workspace.views.importdatasets", { ns: 'classes' })}>
                                    <Button shape="circle" style={{ border: 0, marginLeft: "10px" }} onClick={() => {
                                        this.setVisible(true)
                                    }}><Avatar className="avatar-button-tool-panel" src="images/icon-core/open-modern.svg" /></Button>
                                </Tooltip>
                            </Form.Item>
                        </Col>
                        {selectedRow && <Fragment>
                            <Col>
                                <Form.Item>
                                    <Tooltip placement="top" title={t("sse.Workspace.views.createquery", { ns: 'classes' })}>
                                        <Button shape="circle" style={{ border: 0 }} onClick={() => {
                                            this.handlePanelClick("createQuery", selectedRow.dataset)
                                        }}><Avatar className="avatar-button-tool-panel" src="images/icon-core/query.svg" /></Button>
                                    </Tooltip>
                                </Form.Item>
                            </Col>
                            <Col>
                                <Form.Item>
                                    <Tooltip placement="top" title={t("sse.Workspace.views.viewdata", { ns: 'classes' })}>
                                        <Button shape="circle" style={{ border: 0 }} onClick={() => {
                                            this.handlePanelClick("viewData", selectedRow.dataset)
                                        }}><Avatar className="avatar-button-tool-panel" src="images/icon-core/show-modern.svg" /></Button>
                                    </Tooltip>
                                </Form.Item>
                            </Col>
                            <Col>
                                <Form.Item>
                                    <Tooltip placement="top" title={t("sse.Workspace.views.createlink", { ns: 'classes' })}>
                                        <Button shape="circle" style={{ border: 0 }} onClick={() => {
                                            this.handlePanelClick("createLink", selectedRow.dataset)
                                        }}><Avatar className="avatar-button-tool-panel" src="images/icon-core/linked.svg" /></Button>
                                    </Tooltip>
                                </Form.Item>
                            </Col>
                            <Col>
                                <Form.Item>
                                    <Tooltip placement="top" title={t("sse.Workspace.views.deletedataset", { ns: 'classes' })}>
                                        <Button shape="circle" style={{ border: 0 }} onClick={() => {
                                            this.handlePanelClick("deleteDataset", selectedRow.dataset)
                                        }}><Avatar className="avatar-button-tool-panel" src="images/icon-core/delete-modern.svg" /></Button>
                                    </Tooltip>
                                </Form.Item>
                            </Col>
                        </Fragment>}
                    </Row>
                </Form>
                <Divider style={{ marginTop: 0, marginBottom: 0 }} />
                <div style={{ boxSizing: 'border-box', height: 'calc(100vh - 192px)', width: '100%' }}>
                    {this.createDisplayList()}
                </div>
                {importVisible &&
                    <ImportDatasetView
                        {...this.props}
                        visible={this.state.importVisible}
                        setVisible={(visible) => {
                            this.setVisible(visible)
                        }}
                        setRefresh={(value) => {
                            this.setRefresh(value)
                        }}
                    />}
                <Modal
                    title={`${t("sse.Workspace.views.createlinkto", { ns: 'classes' })} 
                        ${createLinkVisible && this.state.selectedDataset.shortName}`}
                    visible={createLinkVisible}
                    onCancel={this.handleCancel}
                    onOk={this.linkDataset}
                    width="20%"
                >
                    <Select className="ant-select-no-padding"
                        showSearch
                        placeholder={t('sse.Workspace.views.workspace', { ns: ['classes'] })}
                        style={{ marginTop: 8, width: '100%' }}
                        size="small"
                        id="datasets"
                        onChange={(item, e) => {
                            this.setState({ workspaceForLink: { name: item, e_id: e.props.eid, _type_: e.props.type } })
                        }}
                    >
                        {_.sortBy(workspaces, 'name').map(w =>
                            <Select.Option key={w.name} value={w.name} eid={w.e_id} type={w._type_}>{w.name}</Select.Option>)}
                    </Select>
                </Modal>
            </div>
        )
    }
}

export default translate()(WorkspaceGridView);
