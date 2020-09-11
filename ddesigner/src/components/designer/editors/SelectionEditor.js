import React, { Component } from 'react';
import PropTypes from 'prop-types';
import AceEditor from 'react-ace';
import 'brace/mode/sql';
import 'brace/theme/sqlserver';
import 'brace/ext/searchbox';
import { translate } from 'react-i18next'
import update from 'immutability-helper'
import { Form, Tooltip, Avatar, Tree, Row, Col, Divider, Button } from 'antd';
import resource from "./../../../Resource";
import SplitterLayout from 'react-splitter-layout';
import { fullJavaClassName } from '../../../model.js';
import { cupOfCoffee } from '../../../utils/consts';

const TreeNode = Tree.TreeNode

class SelectionEditor extends Component {

    static propTypes = {
        entity: PropTypes.object,
        cellEntity: PropTypes.object
    }

    constructor(...args) {
        super(...args);
        this.aceEditor = React.createRef();
        this.state = {
            expression: "",
            sessionId: null,
            queryResult: ""
        }
    }

    createEditor() {
        return <AceEditor
            ref={'aceEditor'}
            mode={'sql'}
            width={''}
            height={'71vh'}
            theme={'sqlserver'}
            fontSize={15}
            editorProps={{ $blockScrolling: Infinity }}
            value={this.state.expression}
            onChange={newValue => this.editorOnChange(newValue)}
            showPrintMargin={false}
            debounceChangePeriod={500}
        />
    }

    editorOnChange(newValue) {
        this.setState({ expression: newValue })
    }

    saveExpression() {
        const newValue = this.state.expression
        this.props.updateNodeEntity(update(this.props.cellEntity, { $merge: { expression: newValue } }), this.props.cellEntity)
    }

    createTreePane() {
        const { inputPort } = this.props.cellEntity
        return inputPort && <Tree
            showLine
            onSelect={
                (key, event) => this.treeOnClick(event)
            }
        >
            {inputPort.fields.map((field, index) =>
                <TreeNode title={field.name + ' ' + field.dataTypeDomain} key={index + '-' + field.name} data={field.name} />)}
        </Tree>
    }

    treeOnClick(event) {
        if (event.node.props.data) {
            const value = event.node.props.data
            this.refs.aceEditor.editor.insert(value)
            this.refs.aceEditor.editor.focus()
        }
    }

    runValidation() {
        const { sessionId, expression } = this.state
        const { entity, cellEntity } = this.props
        const newFields = this.props.cellEntity.inputPort.fields.map(fl =>
            ({ name: fl.name, dataTypeDomain: fl.dataTypeDomain, javaDomain: fullJavaClassName[fl.dataTypeDomain] }))
        const inputPort = update(this.props.cellEntity.inputPort, { $merge: { fields: newFields } })
        this.setState({ queryResult: cupOfCoffee })
        resource.call({
            session: sessionId,
            checkpoint: false,
            expression: expression,
            inputPort: inputPort,
            outputPort: cellEntity.outputPort,
            label: cellEntity.label,
            name: cellEntity.name,
            parent: { e_id: entity.e_id, name: entity.name, _type_: entity._type_ },
            _type_: cellEntity._type_
        }, "test", {}).then(json => {
            if (!json.result.valueCount) {
                this.setState({ queryResult: 'OK', session: json.sessionId })
            } else {
                this.setState({ queryResult: this.getUsableErrorView(json.result.values[0]), session: json.sessionId })
            }
        })
    }

    getUsableErrorView(queryError) {
        return queryError.status + '\n' + queryError.evalue + '\n' + queryError.traceback.join('')
    }

    componentDidMount() {
        const { cellEntity } = this.props
        if (cellEntity.expression) {
            this.setState({ expression: cellEntity.expression })
        }
    }

    render() {
        const { t } = this.props
        const { queryResult } = this.state
        return (
            <div style={{ height: 'calc(100vh - 149px)' }}>
                <SplitterLayout
                    customClassName='splitter-layout splitter-sql-editor'
                    percentage={true}
                    primaryIndex={0}
                    vertical={true}
                    primaryMinSize={15}
                    secondaryMinSize={15}
                    secondaryInitialSize={15}
                >
                    <div>
                        <Row >
                            <Col span={20}>
                            <Form layout={"inline"}>
                                {queryResult === 'OK' && <Form.Item wrapperCol={{ span: 2, push: 14 }}>
                                    <Tooltip placement="top" title={t("save")}>
                                        <Button id="save" shape="circle" style={{ border: 0 }} onClick={() => {
                                            this.saveExpression()
                                        }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/save-modern.svg"} />
                                        </Button>
                                    </Tooltip>
                                </Form.Item>}
                                <Form.Item wrapperCol={{ span: 2, push: 14 }}>
                                    <Tooltip placement="top" title={t("check")}>
                                        <Button id="check" shape="circle" style={{ border: 0 }} onClick={() => {
                                            this.runValidation()
                                        }}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/check-modern.svg"} />
                                        </Button>
                                    </Tooltip>
                                </Form.Item>
                            </Form>
                            <Divider style={{marginTop: 0, marginBottom: 0}}/>
                                {this.createEditor()}
                            </Col>
                            <Col span={4}>
                                <div style={{ height: 'calc(100vh - 280px)', overflow: 'auto' }}>
                                    {this.createTreePane()}
                                </div>
                            </Col>
                        </Row>
                    </div>
                    <div>
                        {queryResult ? <AceEditor
                            mode={'scala'}
                            width={''}
                            height={'80vh'}
                            theme={'tomorrow'}
                            fontSize={15}
                            editorProps={{ $blockScrolling: Infinity }}
                            value={queryResult}
                            showPrintMargin={false}
                            showGutter={false}
                            focus={false}
                            readOnly={true}
                            minLines={5}
                            highlightActiveLine={false}
                        /> : undefined}
                    </div>
                </SplitterLayout>
            </div>
        )
    }
}

export default translate()(SelectionEditor);
