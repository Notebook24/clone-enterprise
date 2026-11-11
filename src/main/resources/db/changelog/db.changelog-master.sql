--liquibase formatted sql

--changeset enlistment:baseline-create-subject
create table subject (
    subject_id varchar(255) not null
);

--changeset enlistment:baseline-create-subject-prerequisites
create table subject_prerequisites (
    prerequisites_subject_id varchar(255) not null,
    subject_subject_id varchar(255) not null
);

--changeset enlistment:baseline-create-room
create table room (
    capacity integer not null,
    name varchar(255) not null
);

--changeset enlistment:baseline-create-admin
create table admin (
    id integer not null,
    firstname varchar(255),
    lastname varchar(255)
);

--changeset enlistment:baseline-create-section
create table section (
    days smallint,
    end_time time(6),
    number_of_students integer not null,
    start_time time(6),
    room_name varchar(255),
    section_id varchar(255) not null,
    subject_subject_id varchar(255)
);

--changeset enlistment:baseline-create-room-sections
create table room_sections (
    room_name varchar(255) not null,
    sections_section_id varchar(255) not null
);

--changeset enlistment:baseline-create-student
create table student (
    student_number integer not null,
    firstname varchar(255),
    lastname varchar(255)
);

--changeset enlistment:baseline-create-student-sections
create table student_sections (
    student_student_number integer not null,
    sections_section_id varchar(255) not null
);

--changeset enlistment:baseline-create-student-subjects-taken
create table student_subjects_taken (
    student_student_number integer not null,
    subjects_taken_subject_id varchar(255) not null
);

--changeset enlistment:baseline-subject-primary-key
alter table subject add constraint subject_pkey primary key (subject_id);

--changeset enlistment:baseline-room-primary-key
alter table room add constraint room_pkey primary key (name);

--changeset enlistment:baseline-admin-primary-key
alter table admin add constraint admin_pkey primary key (id);

--changeset enlistment:baseline-section-primary-key
alter table section add constraint section_pkey primary key (section_id);

--changeset enlistment:baseline-student-primary-key
alter table student add constraint student_pkey primary key (student_number);

--changeset enlistment:baseline-section-days-check
alter table section add constraint section_days_check check (days between 0 and 2);

--changeset enlistment:baseline-room-sections-section-unique
alter table room_sections add constraint room_sections_sections_section_id_key unique (sections_section_id);

--changeset enlistment:baseline-room-sections-section-fk
alter table room_sections add constraint fk18tmp1kdjwlixo8tcyag4xnv foreign key (sections_section_id) references section;

--changeset enlistment:baseline-room-sections-room-fk
alter table room_sections add constraint fkfd4bv684oyhmv5ntq0gll0exl foreign key (room_name) references room;

--changeset enlistment:baseline-section-room-fk
alter table section add constraint fkb7vxfqjj8qa0r38kh4qs1eac1 foreign key (room_name) references room;

--changeset enlistment:baseline-section-subject-fk
alter table section add constraint fkn23lvqkfvxq9wo0w6qt9xgomd foreign key (subject_subject_id) references subject;

--changeset enlistment:baseline-student-sections-section-fk
alter table student_sections add constraint fk89rtc8dmim7fd4lulgkecplhj foreign key (sections_section_id) references section;

--changeset enlistment:baseline-student-sections-student-fk
alter table student_sections add constraint fkcbuj7ic505urpqreop2xuwstg foreign key (student_student_number) references student;

--changeset enlistment:baseline-student-subjects-taken-subject-fk
alter table student_subjects_taken add constraint fkitrqnu6k56yf07ntuo8q1rast foreign key (subjects_taken_subject_id) references subject;

--changeset enlistment:baseline-student-subjects-taken-student-fk
alter table student_subjects_taken add constraint fkr7ky8jeghxfcdqfugflneppgv foreign key (student_student_number) references student;

--changeset enlistment:baseline-subject-prerequisites-prerequisite-fk
alter table subject_prerequisites add constraint fk6ta30r0qhlti2fsjg762emtwb foreign key (prerequisites_subject_id) references subject;

--changeset enlistment:baseline-subject-prerequisites-subject-fk
alter table subject_prerequisites add constraint fkogx9mcr8od8y3uf7cxb0spqed foreign key (subject_subject_id) references subject;
