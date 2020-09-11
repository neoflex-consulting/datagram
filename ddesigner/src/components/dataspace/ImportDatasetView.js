import React, { Component } from 'react';
import { translate } from "react-i18next";
import { Modal, Row, Col, Button } from 'antd';
import resource from "./../../Resource";
import _ from 'lodash';
import { AgGridReact } from 'ag-grid-react';
import 'ag-grid/dist/styles/ag-grid.css';
import 'ag-grid/dist/styles/ag-theme-balham.css';
import {getLinkedEntity} from './../../utils/datasetMethods';

class ImportDatasetView extends Component {
    constructor(...args) {
        super(...args);
        this.state = {
            workspaceDatasets: [],
            workspaceList: [],
            filteredDatasets: []
        }
        this.workspaceGrid = React.createRef();
        this.datasetGrid = React.createRef();
        this.importing = false;
    }

    getWorkspaceDatasetList() {
        const { entity, activeObject } = this.props
        if (entity.e_id) {
            resource.query("/api/teneo/select/select type(e),e.e_id,e.shortName,e.workspace.shortName,type(e.workspace),e.workspace.e_id from sse.AbstractDataset e").then(result => {
                if (result) {
                    const arrayWithKeys = result.map(e => ({ _type_: e[0], e_id: e[1], shortName: e[2], workspace: {shortName: e[3], _type_: e[4], e_id: e[5]} }))
                    const sortedDatasets = _.sortBy(arrayWithKeys, 'workspace.shortName')
                    let workspacePlainList = _.uniqBy(sortedDatasets.map(w => ({ workspace: w.workspace.shortName, _type_: w.workspace._type_ })), 'workspace')
                    workspacePlainList = _.filter(workspacePlainList, w => w.workspace !== activeObject.name)
                    this.setState({ workspaceDatasets: sortedDatasets, workspaceList: workspacePlainList })
                }
            })
        }
    }

    showModal = () => {
        this.props.setVisible(true)
    }

    handleCancel = (e) => {
        this.props.setVisible(false)
    }

    importDatasetsIntoWorkspace() {
        const selectedDatasets = this.refs.datasetGrid.api.getSelectedRows()

        if (selectedDatasets.length > 10) {
            resource.logError("The max number of imported datasets is 10.")
            return
        }
        let promises = []
        this.importing = true

        selectedDatasets.forEach(el => {
            getLinkedEntity(el, this.props.activeObject, newEntity => {
                promises.push(resource.saveEntity(newEntity))
                if(promises.length === selectedDatasets.length){
                    Promise.all(promises).then(() => {
                        this.props.setRefresh(true)
                        this.props.setVisible(false)
                    }
                    ).catch(() => {
                        this.props.setRefresh(true)
                        this.props.setVisible(false)
                    })
                }
            })
        })
        
    }

    getFilteredDatasets(selectedWorkspaces) {
        const { workspaceDatasets } = this.state
        const filtered = _.filter(workspaceDatasets, (element) => {
            return selectedWorkspaces.findIndex(ws => ws.workspace === element.workspace.shortName) >= 0
        })
        const rowData = filtered.map(d => ({ shortName: d.shortName, _type_: d._type_, workspace: d.workspace, workspace_name: d.workspace.shortName, e_id: d.e_id, name: d.name }))
        this.setState({ filteredDatasets: rowData })
    }

    componentDidMount() {
        this.getWorkspaceDatasetList()
    }

    render() {
        const { t, visible } = this.props
        const { workspaceList, filteredDatasets } = this.state

        return (
            <div>
                <Modal
                    title={t("sse.Workspace.views.importdatasets", { ns: 'classes' })}
                    visible={visible}
                    onCancel={this.handleCancel}
                    width="70%"
                    footer={[
                        <Button key="back" onClick={this.handleCancel}>{t("cancel")}</Button>,
                        <Button key="submit" type="primary" loading={this.importing}
                            onClick={() => this.importDatasetsIntoWorkspace()}>
                            {t("sse.Workspace.views.import", { ns: 'classes' })}
                        </Button>]
                    }
                >
                    <Row gutter={8}>
                        <Col span={12}>
                            <div style={{ height: '65vh', overflow: 'auto' }}>
                                <div style={{ boxSizing: "border-box", height: "100%", width: "100%" }} className="ag-theme-balham">
                                    <AgGridReact
                                        ref={"workspaceGrid"}
                                        columnDefs={[
                                            { headerName: 'Workspace', field: 'workspace', sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' } },
                                            { headerName: 'Type', field: '_type_', sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' } }
                                        ]}
                                        rowData={workspaceList}
                                        enableColResize={'true'}
                                        pivotHeaderHeight={'true'}
                                        enableSorting={true}
                                        sortingOrder={["desc", "asc", null]}
                                        enableFilter={true}
                                        rowSelection={'multiple'}
                                        rowMultiSelectWithClick={true}
                                        onSelectionChanged={() => {
                                            this.getFilteredDatasets(this.refs.workspaceGrid.api.getSelectedRows())
                                        }}
                                    />
                                </div>
                            </div>
                        </Col>
                        <Col span={12}>
                            <div style={{ height: '65vh', overflow: 'auto' }}>
                                <div style={{ boxSizing: "border-box", height: "100%", width: "100%" }} className="ag-theme-balham">
                                    <AgGridReact
                                        ref={"datasetGrid"}
                                        columnDefs={[
                                            { headerName: 'Dataset', field: 'shortName', sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' } },
                                            { headerName: 'Type', field: '_type_', sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' } },
                                            { headerName: 'Workspace', field: 'workspace_name', sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' } }
                                        ]}
                                        rowData={filteredDatasets}
                                        enableColResize={'true'}
                                        pivotHeaderHeight={'true'}
                                        enableSorting={true}
                                        sortingOrder={["desc", "asc", null]}
                                        enableFilter={true}
                                        rowSelection={'multiple'}
                                        rowMultiSelectWithClick={true}
                                    />
                                </div>
                            </div>
                        </Col>
                    </Row>
                </Modal>
            </div>
        )
    }
}

export default translate()(ImportDatasetView);
