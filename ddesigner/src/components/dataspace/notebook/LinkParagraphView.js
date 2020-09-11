import React, { Component } from 'react';
import { translate } from "react-i18next";
import { Modal, Row, Col, Button } from 'antd';
import resource from "./../../../Resource";
import _ from 'lodash';
import { AgGridReact } from 'ag-grid-react';
import 'ag-grid/dist/styles/ag-grid.css';
import 'ag-grid/dist/styles/ag-theme-balham.css';
import update from 'immutability-helper';

class LinkParagraphView extends Component {
    constructor(...args) {
        super(...args);
        this.state = {
            notebookList: [],
            paragraphList: [],
            notebook: null
        }
        this.paragraphGrid = React.createRef();
        this.importing = false;
    }

    getNotebookList() {
        const { entity } = this.props
        if (entity.e_id) {
            resource.query("/api/teneo/select/select type(e),e.e_id,e.shortName,e.name from sse.LinkableNotebook e").then(result => {
                if (result) {
                    const arrayWithKeys = result.map(e => ({ _type_: e[0], e_id: e[1], shortName: e[2], name: e[3] }))
                    const sortedNotebooks = _.sortBy(arrayWithKeys, 'workspace.shortName')
                    this.setState({ notebookList: sortedNotebooks })
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

    linkParagraph() {
        const { entity, index, updateEntity } = this.props
        const {notebook} = this.state
        const selectedParagraph = this.paragraphGrid.current.api.getSelectedRows()
        if(selectedParagraph[0]){
            const updatedParagraphs = update(entity.paragraphs, {
                [index]: { body: { $set:{linkNotebook: notebook, paragraphName: selectedParagraph[0].name, _type_: "sse.LinkBody"}}, result: { $set: null }, status: { $set: "NEW" } }
            })
            updateEntity({ 'paragraphs': updatedParagraphs })
            this.props.saveLinkedToState(notebook.paragraphs.find(p=>p.name === selectedParagraph[0].name))
            this.props.setVisible(false)
        }else{
            resource.logError("Nothing to import!")
        }
    }

    getParagraphs(selectedNotebook) {
        const { paragraph } = this.props
        if (selectedNotebook.length > 0 && selectedNotebook[0].e_id) {
            resource.query("/api/teneo/select/select e from sse.LinkableNotebook e where e.e_id=" + selectedNotebook[0].e_id).then(result => {
                if (result) {
                    const paragraphs = result[0].paragraphs.map(p=>({ name: p.name, title: p.title, code: p.body.text, status: p.status }))
                    const filteredList = paragraphs.filter(p=>p.name !== paragraph.name)
                    this.setState({ paragraphList: filteredList, notebook: result[0] })
                }
            })
        }
    }

    componentDidMount() {
        this.getNotebookList()
    }

    render() {
        const { t, visible } = this.props
        const { notebookList, paragraphList } = this.state
        return (
            <div>
                <Modal
                    title={t("sse.Workspace.views.linkparagraph", { ns: 'classes' })}
                    visible={visible}
                    onCancel={this.handleCancel}
                    width="70%"
                    footer={[
                        <Button key="back" onClick={this.handleCancel}>{t("cancel")}</Button>,
                        <Button key="submit" type="primary" loading={this.importing}
                            onClick={() => this.linkParagraph()}>
                            {t("sse.Workspace.views.import", { ns: 'classes' })}
                        </Button>]
                    }
                >
                    <Row gutter={8}>
                        <Col span={8}>
                            <div style={{ height: '65vh', overflow: 'auto' }}>
                                <div style={{ boxSizing: "border-box", height: "100%", width: "100%" }} className="ag-theme-balham">
                                    <AgGridReact
                                        columnDefs={[
                                            { headerName: 'Notebook', field: 'name', sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' } },
                                            { headerName: 'Shortname', field: 'shortName', sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' } }
                                        ]}
                                        rowData={notebookList}
                                        enableColResize={'true'}
                                        pivotHeaderHeight={'true'}
                                        enableSorting={true}
                                        sortingOrder={["desc", "asc", null]}
                                        enableFilter={true}
                                        rowSelection={'single'}
                                        rowMultiSelectWithClick={true}
                                        onSelectionChanged={(grid) => this.getParagraphs(grid.api.getSelectedRows())}
                                    />
                                </div>
                            </div>
                        </Col>
                        <Col span={16}>
                            <div style={{ height: '65vh', overflow: 'auto' }}>
                                <div style={{ boxSizing: "border-box", height: "100%", width: "100%" }} className="ag-theme-balham">
                                    <AgGridReact
                                        ref={this.paragraphGrid}
                                        columnDefs={[
                                            { headerName: 'Name', field: 'name', sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' } },
                                            { headerName: 'Title', field: 'title', sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' } },
                                            { headerName: 'Code', field: 'code', sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' } },
                                            { headerName: 'Status', field: 'status', sortingOrder: ["asc", "desc"], cellStyle: { 'font-size': '115%' } }
                                        ]}
                                        rowData={paragraphList}
                                        enableColResize={'true'}
                                        pivotHeaderHeight={'true'}
                                        enableSorting={true}
                                        sortingOrder={["desc", "asc", null]}
                                        enableFilter={true}
                                        rowSelection={'single'}
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

export default translate()(LinkParagraphView);
