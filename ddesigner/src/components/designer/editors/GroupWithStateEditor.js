import React, { Component } from 'react';
import PropTypes from 'prop-types';
import AceEditor from 'react-ace';
import 'brace/mode/scala';
import 'brace/theme/tomorrow';
import 'brace/ext/searchbox';
import { translate } from 'react-i18next'
import { Form, Select, Row, Col, Divider } from 'antd';
import 'ag-grid/dist/styles/ag-grid.css';
import 'ag-grid/dist/styles/ag-theme-balham.css';
import resource from "./../../../Resource";
import SplitterLayout from 'react-splitter-layout';
import _ from 'lodash'
import { cupOfCoffee } from '../../../utils/consts';
import update from 'immutability-helper';
import PortsTree from '../../PortsTree';
import { classExtension } from '../../classExtension.js';
import ace from 'brace';

var Range = ace.acequire('ace/range').Range;

class GroupWithStateEditor extends Component {

    static propTypes = {
        entity: PropTypes.object,
        cellEntity: PropTypes.object
    }

    constructor(...args) {
        super(...args);
        this.aceEditor = React.createRef();
        this.state = {
            showTable: false,
            sessionId: null,
            runResult: null,
            runError: null,
            serverList: null,
            selectedServer: null,
            selectedStep: this.props.cellEntity,
            transformationFullCode: null
        }
    }

    componentDidMount() {
        this.setState({
            serverList: this.getServerList()
        }, () => this.loadDeclarationCode())

        this.refs.aceEditor.editor.commands.on("exec", (e) => {
            var rowCol = this.refs.aceEditor.editor.selection.getCursor();
            var isEnterAtLast = (cursor) => {
                if (rowCol.row + 1 === this.refs.aceEditor.editor.session.getLength()) {
                    if (rowCol.column === 0) {
                        if (e.command.name === "insertstring" && e.args === '\n') {
                            return true;
                        }
                    }
                }
                return false;
            }
            var allowAction = (rowCol) => {
                if (e.command.bindKey && ["Up", "Down", "Left", "Right"].includes(e.command.bindKey.win)) {
                    return true;
                }
                if (rowCol.row < 5) {
                    return false;
                }
                if (isEnterAtLast(rowCol) === true) {
                    return true;
                }
                if (rowCol.row + 1 === this.refs.aceEditor.editor.session.getLength()) {
                    return false;
                }
                return true;
            }
            if (allowAction(rowCol) === false) {
                e.preventDefault();
                e.stopPropagation();
            }
        });
    }

    getServerList() {
        resource.query('/api/teneo/rt.LivyServer/').then(response => {
            this.setState({ serverList: response })
        })
    }

    createEditor(value, height, name) {
        return <AceEditor
            ref={"aceEditor"}
            name={name}
            mode={'scala'}
            width={''}
            height={height}
            theme={'tomorrow'}
            fontSize={15}
            debounceChangePeriod={500}
            editorProps={{ $blockScrolling: Infinity }}
            onChange={(newValue, e) => this.editorOnChange(newValue)}
            onInput={() => this.setEndRange()}
            value={value}
            showPrintMargin={false}
        />
    }

    loadDeclarationCode() {
        var { selectedStep } = this.state
        const entity = this.props.entity
        if (!selectedStep.transformation) {
            selectedStep = _.cloneDeep(selectedStep);
            selectedStep.transformation = entity
        }
        this.setState({ runResult: cupOfCoffee, runError: null })
        resource.call({
            _type_: "etl.GroupWithState",
            node: selectedStep
        }, "functionDeclaration", {}).then(json => {
            this.setState({ declarationCode: json.code + '\n\n' })
        }).catch((e) => {
            this.setState({ runResult: null, runError: null })
        }
        )
    }

    getUsableErrorView(queryError) {
        return queryError.status + '\n' + queryError.evalue + '\n' + queryError.traceback.join('')
    }

    setEndRange() {
        //if(!this.endMarker) {
        this.refs.aceEditor.editor.getSession().removeMarker(this.endMarker)
        let endRange = new Range(this.refs.aceEditor.editor.session.getLength() - 1, 0, this.refs.aceEditor.editor.session.getLength() - 1, 200)
        endRange.start = this.refs.aceEditor.editor.getSession().doc.createAnchor(endRange.start);
        endRange.end = this.refs.aceEditor.editor.getSession().doc.createAnchor(endRange.end);
        this.endMarker = this.refs.aceEditor.editor.getSession().addMarker(endRange, "readOnlyText", "line", true);
        //}
    }

    editorOnChange(newValue) {
        let val = newValue.replace(this.state.declarationCode, '')
        val = val.replace(new RegExp(/\n}$/), '');
        if (val !== this.props.cellEntity.flatMapGroupsWithState) {
            this.props.updateNodeEntity(update(this.props.cellEntity, { $merge: { flatMapGroupsWithState: val } }), this.props.cellEntity)
        }

        if (!this.r) {
            this.r = new Range(0, 0, 4, 0);
            this.refs.aceEditor.editor.getSession().addMarker(this.r, "readOnlyText", "line", true);
        }
        this.setEndRange()
    }

    treeOnClick(event) {
        if (event.node.props.data) {
            const value = event.node.props.data
            this.refs.aceEditor.editor.insert(value)
            this.refs.aceEditor.editor.focus()
        }
    }

    showStepContent() {
        const { serverList, runError, runResult } = this.state
        const Option = Select.Option
        var inputPort = _.cloneDeep(this.props.cellEntity.inputPort ? this.props.cellEntity.inputPort : {});
        //var inputPortOwner = undefined
        var transition = (this.props.entity.transitions || []).find(t => {
            return t.finish === this.props.cellEntity.inputPort
        })
        if (transition) {
            //inputPortOwner =
            classExtension.nodes(this.props.entity).find(n => {
                return n.outputPort === transition.start
            })
        }
        inputPort.alias = "InputRow"
        var outputPort = _.cloneDeep(this.props.cellEntity.outputPort ? this.props.cellEntity.outputPort : {});
        outputPort.alias = "OutputRow"
        var internalState = _.cloneDeep(this.props.cellEntity.internalState ? this.props.cellEntity.internalState : {});
        internalState.alias = "InternalState"

        return (
            <div style={{ overflow: 'hidden', height: 'calc(100vh - 194px)' }}>
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
                        <Form layout={"inline"}>
                            <Form.Item>
                                <Select
                                    showSearch
                                    size="small"
                                    style={{ width: 200, marginRight: 2, marginTop: 2 }}
                                    placeholder={this.props.t('etl.Transformation.attrs.transformationStep.selectServer.caption', { ns: 'classes' })}
                                    optionFilterProp="children"
                                    onChange={(value) => {
                                        this.setState({ selectedServer: value })
                                    }}
                                >
                                    {serverList && serverList.map(item => {
                                        return <Option value={item.name}>{item.name}</Option>
                                    })}
                                </Select>
                            </Form.Item>
                        </Form>
                        <Divider style={{marginTop: 0, marginBottom: 0}}/>
                        <div>
                            <Row >
                                <Col span={20}>
                                    {this.createEditor(this.state.declarationCode + this.props.cellEntity.flatMapGroupsWithState + '\n}', '67vh', 'stepEditor')}
                                </Col>
                                <Col span={4}>
                                    <div style={{ height: 'calc(100vh - 280px)', overflow: 'auto' }}>
                                        <PortsTree ports={[inputPort, internalState, outputPort]} treeOnClick={(event) => this.treeOnClick(event)} />
                                    </div>
                                </Col>
                            </Row>
                        </div>
                    </div>
                    <div>
                        {runError ? (
                            <AceEditor
                                mode={'scala'}
                                width={''}
                                height={'80vh'}
                                theme={'tomorrow'}
                                fontSize={15}
                                editorProps={{ $blockScrolling: Infinity }}
                                value={runError}
                                showPrintMargin={false}
                                showGutter={false}
                                focus={false}
                                readOnly={true}
                                minLines={5}
                                highlightActiveLine={false}
                            />
                        ) : (
                                runResult && <AceEditor
                                    mode={'scala'}
                                    width={''}
                                    height={'70vh'}
                                    theme={'tomorrow'}
                                    fontSize={15}
                                    editorProps={{ $blockScrolling: Infinity }}
                                    value={runResult}
                                    showPrintMargin={false}
                                    showGutter={false}
                                    focus={false}
                                    readOnly={true}
                                    minLines={5}
                                    highlightActiveLine={false}
                                />
                            )
                        }
                    </div>
                </SplitterLayout>

            </div>
        )
    }

    render() {
        return (
            <div style={{ overflow: 'hidden', height: 'calc(100vh - 150px)' }}>
                {this.showStepContent()}
            </div>
        )
    }

}

export default translate()(GroupWithStateEditor);
