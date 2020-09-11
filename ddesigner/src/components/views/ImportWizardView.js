import React, { Component } from 'react';
import PropTypes from 'prop-types';
import { translate } from 'react-i18next'
import {Input, Button} from 'antd';
import 'ag-grid/dist/styles/ag-grid.css';
import 'ag-grid/dist/styles/ag-theme-balham.css';
import {ContextStep, ObjectsStep, FinetuningStep} from './../wizard/stepComponents';
import resource from '../../Resource';

const steps = {
    1: ContextStep,
    2: ObjectsStep,
    3: FinetuningStep
}

class ImportWizardView extends Component {

    static propTypes = {
        entity: PropTypes.object
    }

    constructor(...args) {
        super(...args);
        this.state = {
            editingHeadline: false,
            currentStep: 1
        }
    }

    setNewName(name) {
        const { updateEntity, entity } = this.props
        if (name !== entity.name) {
            updateEntity({ 'name': name })
        }
        this.setState({ editingHeadline: false })
    }

    handlePrevClick = () => {
        const {currentStep} = this.state
        this.setState({currentStep: currentStep - 1})
    }

    handleNextClick = () => {
        const {currentStep} = this.state
        this.setState({currentStep: currentStep + 1})
    }

    handleImport =() => {

    }

    handleRefreshScheme = () => {
        const { entity, refresh } = this.props
        resource.call(entity, 'loadMetadata').then(result => {
            resource.logInfo(`Created: ${result.created}, Deleted: ${result.deleted}`)
            refresh()
        })
    }

    getHelpText(){
        const { currentStep } = this.state
        switch (currentStep) {
            case 1:
                return this.props.t('rt.ImportWizard.views.importwizard', { ns: ['classes'] })
            case 2:
                return this.props.t('rt.ImportWizard.views.selectobjects', { ns: ['classes'] })
            case 3:
                return this.props.t('rt.ImportWizard.views.advancedsettings', { ns: ['classes'] })
            default: return ''
        }
    }

    componentWillUnmount(){
        window.removeEventListener('loadMetadata', ()=>{ this.setState({ currentStep: 1 }) })
    }

    componentDidMount(){
        window.addEventListener('loadMetadata',()=>{ 
            this.setState({ currentStep: 1 })
        })
    }

    render() {
        const { editingHeadline, currentStep } = this.state
        const { t, entity } = this.props
        const Step = steps[currentStep]
        return (
            <div style={{ height: 'calc(100vh - 155px)', padding: '13px', overflow: 'auto' }}>
                <div className="pretty-box">
                    <span style={{ fontSize: '32px', fontWeight: '500' }} onClick={() => this.setState({ editingHeadline: true })}>
                        {editingHeadline ?
                            <Input autoFocus
                                style={{
                                    fontSize: '32px',
                                    fontWeight: '500',
                                    marginTop: '14px',
                                    paddingBottom: '14px',
                                    paddingLeft: '1px'
                                }}
                                defaultValue={entity.name}
                                onBlur={(e) => {
                                    this.setNewName(e.target.value)
                                }}
                                onPressEnter={(e) => {
                                    this.setNewName(e.target.value)
                                }}
                            />
                            :
                            <span style={{ fontSize: '32px', fontWeight: '500' }} onClick={() => this.setState({ editingHeadline: true })}>
                                {entity.name ? entity.name : "Name"}
                            </span>
                        }
                    </span>
                    <div onClick={() => this.setState({ editingHeadline: true })}>
                        {this.getHelpText()}
                    </div>
                    {entity && entity.workflowParameters && Step && <Step 
                        entity = {entity} 
                        updateEntity = {this.props.updateEntity}
                        selectObject = {this.props.selectObject}
                    />}
                    <br />
                    {entity && entity.workflowParameters && (currentStep > 1) && <Button type="dashed" onClick={this.handlePrevClick}>{t('previous')}</Button>}
                    {entity && entity.workflowParameters && (currentStep === 2) && <Button type="default" style={{marginLeft: '10px', backgroundColor: '#9bd46d',borderColor: '#9bd46d',color: '#fff'}} 
                        onClick={this.handleRefreshScheme}>{t('refresh')}</Button>}
                    {entity && entity.workflowParameters && (currentStep < 3) && <Button type="primary" style={{marginLeft: '10px'}} onClick={this.handleNextClick}>{t('next')}</Button>}
                </div>
            </div>
        )
    }

}

export default translate()(ImportWizardView);
