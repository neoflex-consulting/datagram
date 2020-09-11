import React, { Component, Fragment } from 'react';
import PropTypes from 'prop-types';
import AceEditor from 'react-ace';
import 'brace/mode/scala';
import 'brace/theme/tomorrow';
import 'brace/ext/searchbox';
import { translate } from 'react-i18next'
import { Form, Tooltip, Avatar, Tabs, Select, Button, Divider, Row, Col } from 'antd';
import 'ag-grid/dist/styles/ag-grid.css';
import 'ag-grid/dist/styles/ag-theme-balham.css';
import resource from "./../../../Resource";
import SplitPane from 'react-split-pane';
import Pane from 'react-split-pane/lib/Pane';
import _ from 'lodash'
import { cupOfCoffee } from '../../../utils/consts';

const TabPane = Tabs.TabPane

class SourceCodeEditor extends Component {

	static propTypes = {
		entity: PropTypes.object,
		cellEntity: PropTypes.object
	}

	constructor(...args) {
		super(...args);
		this.aceEditor = React.createRef();
		this.console = React.createRef();
		this.state = {
			showTable: false,
			sessionId: null,
			runResult: null,
			runError: null,
			serverList: null,
			selectedServer: null,
			stepCode: null,
			fullCode: null,
			selectedStep: null,
			transformationFullCode: null
		}
		this.splitterPosition = '50%'
	}

	getServerList() {
		resource.query('/api/teneo/rt.LivyServer/').then(response => {
			this.setState({ serverList: response })
		})
	}

	getTransformationSteps() {
		const entity = this.props.entity
		const steps = entity.sources ? _.concat(entity.sources, entity.targets, entity.transformationSteps) : undefined
		return steps
	}

	getStaticHeight(editor) {
		const lineHeight = editor.renderer.lineHeight
		const screenLength = editor.getSession().getDocument().getLength()
		const scrollBarWidth = editor.renderer.scrollBar.width
		const height = String((screenLength * lineHeight) + scrollBarWidth) + 'px'
		return height
	}

	createEditor(value, height, name, onChangeFunction) {
		return <AceEditor
			ref={"aceEditor"}
			name={name}
			mode={'scala'}
			width={'100%'}
			height={'100%'}
			theme={'tomorrow'}
			fontSize={15}
			debounceChangePeriod={500}
			editorProps={{ $blockScrolling: Infinity }}
			onChange={newValue => onChangeFunction(newValue)}
			value={value}
			showPrintMargin={false}
		/>
	}

	saveCode(content) {
		const entity = this.props.activeObject
		resource.call({
			_type_: entity._type_,
			e_id: entity.e_id,
			fileContent: content
		}, 'writeMainJobFile', {}).then(resource.logInfo('Saved'))
	}

	showTransformationContent() {
		const { fullCode } = this.state
		const { t } = this.props
		return (
			<div style={{
				display: 'flex', 
				height: 'calc(100vh - 200px)',
				flexDirection: 'column',
    			overflow: 'hidden'
			}}>
				<Form layout={"inline"}>
					<Form.Item wrapperCol={{ span: 2, push: 14 }}>
						<Tooltip placement="top" title={t("loadfullcode")}>
							<Button id="loadfullcode" shape="circle" style={{ border: 0 }} onClick={() => {
								this.loadFullCode()
							}}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/upload-modern.svg"} />
							</Button>
						</Tooltip>
					</Form.Item>
					<Form.Item wrapperCol={{ span: 2, push: 14 }}>
						<Tooltip placement="top" title={t("save")}>
							<Button id="save" shape="circle" style={{ border: 0 }} onClick={() => {
								this.saveCode(this.state.fullCode)
							}}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/save-modern.svg"} />
							</Button>
						</Tooltip>
					</Form.Item>
				</Form>
				<Divider style={{ marginTop: 0, marginBottom: 0 }} />
				{this.createEditor(fullCode ? fullCode : undefined, '100%', 'transformEditor', (newValue) => this.setState({ fullCode: newValue }))}
			</div>
		)
	}

	runCode() {
		const entity = this.props.activeObject
		const { selectedStep, sessionId, serverList, selectedServer, stepCode } = this.state
		if (selectedServer) {
			this.setState({ runResult: cupOfCoffee, runError: null })
			resource.call({
				_type_: entity._type_,
				e_id: entity.e_id,
				nodeName: selectedStep,
				sessionId: sessionId,
				fileContent: stepCode,
				server: serverList.find(list => list.name === selectedServer)
			}, "runPart", {}).then(json => {
				!json.result.valueCount ? (
					this.setState({ runResult: json.result["text/plain"], runError: null })
				) : (
						this.setState({ runResult: null, runError: this.getReadableErrorView(json.result.values[0]) })
					)
			}).catch(() => this.setState({ runResult: null, runError: null }))
		} else {
			resource.logError('Server is not selected')
		}
	}

	getReadableErrorView(queryError) {
		return queryError.status + '\n' + queryError.evalue + '\n' + queryError.traceback.join('')
	}

	loadCode() {
		const entity = this.props.activeObject
		const step = this.state.selectedStep
		if (step) {
			this.setState({ stepCode: null })
			resource.call({
				_type_: entity._type_,
				e_id: entity.e_id,
				nodeName: step,
			}, 'partJobFile', {}).then(json => json.result === true ? this.setState({ stepCode: json.fileContent }) : undefined).catch(
				() => this.setState({ stepCode: null })
			)
		} else {
			resource.logError('Step is not selected')
		}
	}

	loadFullCode(type) {
		const entity = this.props.activeObject
		this.setState({ fullCode: null })
		resource.call({
			_type_: entity._type_,
			e_id: entity.e_id,
		}, 'mainJobFile', {}).then(json => json.result === true ? this.setState({ fullCode: json.fileContent }) : undefined).catch(
			() => this.setState({ fullCode: null })
		)
	}

	createConsole(value) {
		return (
			<AceEditor
				ref={"console"}
				mode={'scala'}
				width={''}
				height={'100%'}
				theme={'tomorrow'}
				fontSize={15}
				minLines={5}
				editorProps={{ $blockScrolling: Infinity }}
				value={value}
				showPrintMargin={false}
				showGutter={false}
				focus={false}
				readOnly={true}
				highlightActiveLine={false}
			/>
		)
	}

	showStepContent() {
		const { t } = this.props
		const { serverList, stepCode, runError, runResult } = this.state
		const Option = Select.Option
		const transformationSteps = this.getTransformationSteps()
		return (
			<Row>
				<Col span={24}>
					<Form layout={"inline"}>
						<Form.Item wrapperCol={{ span: 2, push: 14 }}>
							<Tooltip placement="top" title={t("run")}>
								<Button id="run" shape="circle" style={{ border: 0 }} onClick={() => {
									this.runCode()
								}}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/arrow-right-modern.svg"} />
								</Button>
							</Tooltip>
						</Form.Item>
						<Form.Item wrapperCol={{ span: 2, push: 14 }}>
							<Tooltip placement="top" title={t("loadcode")}>
								<Button id="loadcode" shape="circle" style={{ border: 0 }} onClick={() => {
									this.loadCode()
								}}><Avatar className="avatar-button-tool-panel" src={"images/icon-core/upload-modern.svg"} />
								</Button>
							</Tooltip>
						</Form.Item>
						<Form.Item wrapperCol={{ span: 2, push: 2 }}>
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
								{serverList && serverList.map((item, index) => {
									return <Option key={`${item.name}_${index}`} value={item.name}>{item.name}</Option>
								})}
							</Select>
						</Form.Item>
						<Form.Item wrapperCol={{ span: 2, push: 1 }}>
							<Select
								showSearch
								size="small"
								style={{ width: 200, marginRight: 2, marginTop: 2 }}
								placeholder={this.props.t('etl.Transformation.attrs.transformationStep.step.caption', { ns: 'classes' })}
								optionFilterProp="children"
								onChange={(value) => {
									this.setState({ selectedStep: value })
								}}
							>
								{transformationSteps && _.sortBy(transformationSteps, [s => s.name]).map((item, index) => {
									return <Option key={`${item.name}_${index}`} value={item.name}>{item.name}</Option>
								})}
							</Select>
						</Form.Item>
					</Form>
					<Divider style={{ marginTop: 0, marginBottom: 0 }} />
					<div style={{ overflow: 'auto', height: 'calc(100vh - 232px)' }}>
						<SplitPane
							split="horizontal"
							primary="first"
							onChange={(values) => {
								this.refs.aceEditor.editor.resize()
								if (this.refs.console) {
									this.refs.console.editor.resize()
								}
								this.splitterPosition = values[1]
							}}
						>
							<Pane style={{ height: '100%', width: '100%', overflow: 'auto' }}>
								{this.createEditor(stepCode ? stepCode : undefined, '100%', 'stepEditor', (newValue) => this.setState({ stepCode: newValue }))}
							</Pane>
							<Pane
								initialSize={this.splitterPosition}
								//minSize={'10%'}
								style={{ height: '100%', width: '100%', overflow: 'auto', marginBottom: '10px' }}
							>
								{runResult && this.createConsole(runResult)}
								{runError && this.createConsole(runError)}
							</Pane>
						</SplitPane>
					</div>
				</Col>
			</Row>
		)
	}
	
	componentDidMount() {
		this.setState({
			serverList: this.getServerList(),
			fullCode: this.loadFullCode()
		})
	}

	componentDidUpdate(prevProps, prevState) {
		if(prevState.fullCode !== this.state.fullCode){
			if (this.refs.aceEditor && this.refs.aceEditor.editor) {
				const newHeight = this.getStaticHeight(this.refs.aceEditor.editor)
				if(newHeight !== this.refs.aceEditor.editor.container.style.height) {
					//this.refs.aceEditor.editor.container.style.height = newHeight
					//this.refs.aceEditor.editor.resize()
				}
			}
		}
	}
	
	render() {
		return (
			<Fragment>
				<Tabs defaultActiveKey="1" animated={false}>
					<TabPane tab={this.props.t('etl.Transformation.attrs.transformationSource.caption', { ns: 'classes' })} key="1">
						{this.showTransformationContent()}
					</TabPane>
					<TabPane tab={this.props.t('etl.Transformation.attrs.transformationStep.caption', { ns: 'classes' })} key="2">
						{this.showStepContent()}
					</TabPane>
				</Tabs>
			</Fragment>
		)
	}

}

export default translate()(SourceCodeEditor);
